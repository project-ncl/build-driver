/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.builddriver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.util.Headers;
import org.apache.commons.text.StringSubstitutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.pnc.api.builddriver.dto.BuildCancelRequest;
import org.jboss.pnc.api.builddriver.dto.BuildCompleted;
import org.jboss.pnc.api.builddriver.dto.BuildRequest;
import org.jboss.pnc.api.builddriver.dto.BuildResponse;
import org.jboss.pnc.api.constants.MDCHeaderKeys;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.buildagent.api.Status;
import org.jboss.pnc.buildagent.api.TaskStatusUpdateEvent;
import org.jboss.pnc.buildagent.client.BuildAgentClient;
import org.jboss.pnc.buildagent.client.BuildAgentClientException;
import org.jboss.pnc.buildagent.client.BuildAgentHttpClient;
import org.jboss.pnc.buildagent.client.HttpClientConfiguration;
import org.jboss.pnc.buildagent.common.http.HttpClient;
import org.jboss.pnc.buildagent.common.http.StringResult;
import org.jboss.pnc.builddriver.dto.CallbackContext;
import org.jboss.pnc.common.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@RequestScoped
public class Driver {

    private static final Logger logger = LoggerFactory.getLogger(Driver.class);

    @Inject
    ManagedExecutor executor;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    JsonWebToken webToken;

    @ConfigProperty(name = "build-driver.self-base-url")
    String selfBaseUrl;

    /**
     * Template for script that is uploaded to the build environment.
     */
    @ConfigProperty(name = "build-driver.script-template")
    String scriptTemplate;

    /**
     * Fail the request when there are no byte reads for a given timeout in milliseconds.
     */
    @ConfigProperty(name = "build-driver.socket-read-timeout", defaultValue = "1000")
    int socketReadTimeout;

    /**
     * Fail the request when there are no bytes written for a given timeout in milliseconds.
     */
    @ConfigProperty(name = "build-driver.socket-write-timeout", defaultValue = "1000")
    int socketWriteTimeout;

    // retry for ~7h with the defaults
    @ConfigProperty(name = "build-driver.invoker-callback.max-retries", defaultValue = "100")
    int invokerMaxRetries;

    @ConfigProperty(name = "build-driver.invoker-callback.wait-before-retry", defaultValue = "500")
    long invokerWaitBeforeRetry;

    @ConfigProperty(name = "build-driver.max-log-size", defaultValue = "94371840") // = 90 * 1024 * 1024 = 90MB
    int maxLogSize;

    @Inject
    HttpClient httpClient;

    public CompletableFuture<BuildResponse> start(BuildRequest buildRequest) {
        List<Request.Header> headers = getHeaders();

        Request executionCompletedCallback;
        try {
            CallbackContext callbackContext = new CallbackContext(
                    buildRequest.getWorkingDirectory(),
                    buildRequest.getCompletionCallback(),
                    buildRequest.isDebugEnabled(),
                    buildRequest.getEnvironmentBaseUrl());

            executionCompletedCallback = new Request(
                    Request.Method.PUT,
                    new URI(Strings.addEndingSlash(selfBaseUrl) + "internal/completed"),
                    headers,
                    callbackContext);
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        HttpClientConfiguration clientConfiguration = HttpClientConfiguration.newBuilder()
                .callback(executionCompletedCallback)
                .termBaseUrl(buildRequest.getEnvironmentBaseUrl())
                .heartbeatConfig(java.util.Optional.ofNullable(buildRequest.getHeartbeatConfig()))
                .build();
        BuildAgentClient buildAgentClient;
        try {
            buildAgentClient = new BuildAgentHttpClient(httpClient, clientConfiguration);
        } catch (BuildAgentClientException e) {
            return CompletableFuture.failedFuture(e);
        }

        String workingDirectory = buildRequest.getWorkingDirectory();
        String buildScript = prepareBuildScript(
                workingDirectory,
                buildRequest.getProjectName(),
                buildRequest.getScmUrl(),
                buildRequest.getScmRevision(),
                buildRequest.getCommand());
        logger.debug("Build script: {}", buildScript);
        Path runScriptPath = Paths.get(workingDirectory, "/run.sh");

        logger.debug("Scheduling script upload ...");
        return buildAgentClient.uploadFile(ByteBuffer.wrap(buildScript.getBytes(StandardCharsets.UTF_8)), runScriptPath)
                .thenAcceptAsync(response -> {
                    logger.info("Script upload completed with status: {}", response.getCode());
                    if (!isSuccess(response.getCode())) {
                        throw new RuntimeException(
                                "Filed to upload build script. Response code: " + response.getCode());
                    }
                }, executor)
                .thenComposeAsync((aVoid) -> {
                    String command = "sh " + runScriptPath;
                    logger.info("Invoking remote command {}.", command);
                    return buildAgentClient.executeAsync(command);
                }, executor)
                .thenApplyAsync(sessionId -> {
                    logger.info("Remote command invoked.");
                    URI buildCancelUrl;
                    try {
                        buildCancelUrl = new URI(Strings.stripEndingSlash(selfBaseUrl) + "/cancel");
                    } catch (URISyntaxException e) {
                        throw new CompletionException(new DriverException("Cannot construct cancel URL.", e));
                    }
                    BuildCancelRequest cancelRequest = new BuildCancelRequest(sessionId, buildRequest.getEnvironmentBaseUrl());
                    Request cancel = new Request(Request.Method.PUT, buildCancelUrl, headers, cancelRequest);
                    return new BuildResponse(cancel, sessionId);
                }, executor);
    }

    public CompletableFuture<Void> completed(TaskStatusUpdateEvent event) {
        // context is de-serialized as HashMap
        CallbackContext context = objectMapper.convertValue(event.getContext(), CallbackContext.class);

        String logPath = context.getWorkingDirectory() + "/console.log";

        Request invokerCallback = context.getInvokerCallback();

        HttpClientConfiguration clientConfiguration = HttpClientConfiguration.newBuilder()
                .termBaseUrl(context.getEnvironmentBaseUrl())
                .build();
        BuildAgentClient buildAgentClient;
        try {
            buildAgentClient = new BuildAgentHttpClient(httpClient, clientConfiguration);
        } catch (BuildAgentClientException e) {
            return CompletableFuture.failedFuture(e);
        }

        org.jboss.pnc.buildagent.api.Status status = event.getNewStatus();
        final boolean debugEnabled;
        CompletableFuture<String> optionallyEnableSsh;
        logger.debug("Script completionNotifier completed with status {}.", status);
        if ((status == org.jboss.pnc.buildagent.api.Status.FAILED
                || status == org.jboss.pnc.buildagent.api.Status.SYSTEM_ERROR
                || status == org.jboss.pnc.buildagent.api.Status.INTERRUPTED) && context.isEnableDebugOnFailure()) {
            debugEnabled = true;
            optionallyEnableSsh = buildAgentClient.executeAsync("/usr/local/bin/startSshd.sh");
        } else {
            debugEnabled = false;
            optionallyEnableSsh = CompletableFuture.completedFuture(null);
        }

        return optionallyEnableSsh.thenCompose(s -> {
            logger.debug("Downloading file to String Buffer from {}", logPath);
            return buildAgentClient.downloadFile(Paths.get(logPath), maxLogSize);
        }).thenApplyAsync(response -> {

            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("==== ").append(logPath).append(" ====\n");

            StringResult stringResult = response.getStringResult();
            logBuilder.append(stringResult.getString());

            if (!stringResult.isComplete()) {
                logger.warn("\nLog file was not fully downloaded from: {}", logPath);
                logBuilder.append("----- build log was cut -----\n");
                if (Status.COMPLETED.equals(status)) { // status is success
                    logBuilder.append(
                            "----- build completed successfully but it is marked as failed due to log overflow. Max log size is "
                                    + maxLogSize + " -----\n");
                    return new BuildCompleted(
                            logBuilder.toString(),
                            ResultStatus.FAILED,
                            event.getOutputChecksum(),
                            debugEnabled,
                            null);
                }
            }

            return new BuildCompleted(
                    logBuilder.toString(),
                    TypeConverters.toResultStatus(status),
                    event.getOutputChecksum(),
                    debugEnabled,
                    null);
        }, executor).handleAsync((completedBuild, throwable) -> {
            if (throwable != null) {
                return completedBuild.toBuilder().throwable(throwable).buildStatus(ResultStatus.SYSTEM_ERROR).build();
            } else {
                return completedBuild;
            }
        }, executor).thenCompose(completedBuild -> notifyInvoker(completedBuild, invokerCallback));
    }

    public CompletableFuture<HttpClient.Response> cancel(BuildCancelRequest buildCancelRequest) {
        HttpClientConfiguration clientConfiguration = HttpClientConfiguration.newBuilder()
                .termBaseUrl(buildCancelRequest.getBuildEnvironmentBaseUrl())
                .build();
        BuildAgentClient buildAgentClient;
        try {
            buildAgentClient = new BuildAgentHttpClient(httpClient, clientConfiguration);
        } catch (BuildAgentClientException e) {
            throw new CompletionException("Cannot create build agent client.", e);
        }
        return buildAgentClient.cancel(buildCancelRequest.getBuildExecutionId());
    }

    private CompletableFuture<Void> notifyInvoker(BuildCompleted buildCompleted, Request callback) {
        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(buildCompleted);
        } catch (JsonProcessingException e) {
            logger.error("Cannot serialize result.", e);
            return CompletableFuture.failedFuture(new DriverException("Cannot serialize result.", e));
        }
        return httpClient
                .invoke(
                        new Request(callback.getMethod(), callback.getUri(), callback.getHeaders()),
                        ByteBuffer.wrap(data),
                        invokerMaxRetries,
                        invokerWaitBeforeRetry,
                        -1L,
                        socketReadTimeout,
                        socketWriteTimeout)
                .thenApply(response -> {
                    if (isSuccess(response.getCode())) {
                        logger.info("Successfully sent buildCompleted to the invoker.");
                    } else {
                        String message = "Error sending buildCompleted to the invoker. Response code: "
                                + response.getCode();
                        logger.error(message);
                        throw new CompletionException(new DriverException(message));
                    }
                    return null;
                });
    }

    private boolean isSuccess(int responseCode) {
        return responseCode >= 200 && responseCode < 300;
    }

    private String prepareBuildScript(
            String workingDirectory,
            String projectName,
            String scmUrl,
            String scmRevision,
            String buildCommand) {
        Map<String, String> values = new HashMap<>();
        values.put("workingDirectory", workingDirectory);
        values.put("projectName", projectName);
        values.put("scmUrl", scmUrl);
        values.put("scmRevision", scmRevision);
        values.put("command", buildCommand);
        return StringSubstitutor.replace(scriptTemplate, values, "%{", "}");
    }

    private List<Request.Header> getHeaders() {
        List<Request.Header> headers = new ArrayList<>();
        headers.add(new Request.Header(Headers.CONTENT_TYPE_STRING, MediaType.APPLICATION_JSON));
        if (webToken.getRawToken() != null) {
            headers.add(new Request.Header(Headers.AUTHORIZATION_STRING, "Bearer " + webToken.getRawToken()));
        }
        headersFromMdc(headers, MDCHeaderKeys.REQUEST_CONTEXT);
        headersFromMdc(headers, MDCHeaderKeys.PROCESS_CONTEXT);
        headersFromMdc(headers, MDCHeaderKeys.TMP);
        headersFromMdc(headers, MDCHeaderKeys.EXP);
        return headers;
    }

    private void headersFromMdc(List<Request.Header> headers, MDCHeaderKeys headerKey) {
        String mdcValue = MDC.get(headerKey.getMdcKey());
        if (!Strings.isEmpty(mdcValue)) {
            headers.add(new Request.Header(headerKey.getHeaderName(), mdcValue));
        }
    }
}
