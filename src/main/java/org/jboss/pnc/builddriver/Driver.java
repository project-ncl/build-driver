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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
import org.jboss.pnc.api.dto.HeartbeatConfig;
import org.jboss.pnc.api.dto.Request;
import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.buildagent.api.Status;
import org.jboss.pnc.buildagent.api.TaskStatusUpdateEvent;
import org.jboss.pnc.buildagent.client.BuildAgentClient;
import org.jboss.pnc.buildagent.client.BuildAgentClientException;
import org.jboss.pnc.buildagent.client.BuildAgentHttpClient;
import org.jboss.pnc.buildagent.client.HttpClientConfiguration;
import org.jboss.pnc.buildagent.common.http.HeartbeatSender;
import org.jboss.pnc.buildagent.common.http.HttpClient;
import org.jboss.pnc.builddriver.dto.CallbackContext;
import org.jboss.pnc.common.Strings;
import org.jboss.pnc.common.otel.OtelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

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

    @Inject
    HttpClient httpClient;

    @Inject
    HeartbeatSender heartbeatSender;

    @WithSpan()
    public CompletableFuture<BuildResponse> start(@SpanAttribute(value = "buildRequest") BuildRequest buildRequest) {
        List<Request.Header> headers = getHeaders();

        Request executionCompletedCallback;
        try {
            CallbackContext callbackContext = new CallbackContext(
                    buildRequest.getWorkingDirectory(),
                    buildRequest.getCompletionCallback(),
                    buildRequest.isDebugEnabled(),
                    buildRequest.getEnvironmentBaseUrl(),
                    buildRequest.getHeartbeatConfig());

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
                .requestHeaders(getRequestHeaders())
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
                buildRequest.getScmTag(),
                buildRequest.getCommand());
        logger.info("Build script: {}", buildScript);
        Path runScriptPath = Paths.get(workingDirectory, "/run.sh");

        Path traceparentPath = Paths.get(workingDirectory, "/otel/traceparent");
        String traceParent = OtelUtils.createTraceParent(Span.current().getSpanContext());

        logger.info("Traceparent {} being uploaded to file {} ...", traceParent, traceparentPath);
        return buildAgentClient
                .uploadFile(ByteBuffer.wrap(traceParent.getBytes(StandardCharsets.UTF_8)), traceparentPath)
                .handleAsync((response, throwable) -> {
                    logger.info("Traceparent upload completed with status: {}", response.getCode());
                    if (throwable != null || !isSuccess(response.getCode())) {
                        logger.error(
                                "Failed to upload traceparent file content, received status: {}, exception:",
                                response.getCode(),
                                throwable);
                    }
                    return null;
                }, executor)
                .thenComposeAsync(Context.current().wrapFunction((aVoid) -> {
                    logger.info("Scheduling script upload ...");
                    return buildAgentClient
                            .uploadFile(ByteBuffer.wrap(buildScript.getBytes(StandardCharsets.UTF_8)), runScriptPath);
                }), executor)
                .thenAcceptAsync(Context.current().wrapConsumer(response -> {
                    logger.info("Script upload completed with status: {}", response.getCode());
                    if (!isSuccess(response.getCode())) {
                        throw new RuntimeException(
                                "Failed to upload build script. Response code: " + response.getCode());
                    }
                }), executor)
                .thenComposeAsync(Context.current().wrapFunction((aVoid) -> {
                    String command = "sh " + runScriptPath;
                    logger.info("Invoking remote command {}.", command);
                    return buildAgentClient.executeAsync(command);
                }), executor)
                .thenApplyAsync(Context.current().wrapFunction(sessionId -> {
                    logger.info("Remote command invoked.");
                    URI buildCancelUrl;
                    try {
                        buildCancelUrl = new URI(Strings.stripEndingSlash(selfBaseUrl) + "/cancel");
                    } catch (URISyntaxException e) {
                        throw new CompletionException(new DriverException("Cannot construct cancel URL.", e));
                    }
                    BuildCancelRequest cancelRequest = new BuildCancelRequest(
                            sessionId,
                            buildRequest.getEnvironmentBaseUrl());
                    Request cancel = new Request(Request.Method.PUT, buildCancelUrl, headers, cancelRequest);
                    return new BuildResponse(cancel, sessionId);
                }), executor);
    }

    private List<Request.Header> getRequestHeaders() {
        return Collections
                .singletonList(new Request.Header(HttpHeaders.AUTHORIZATION, "Bearer " + webToken.getRawToken()));
    }

    @WithSpan()
    public CompletableFuture<Void> completed(@SpanAttribute(value = "event") TaskStatusUpdateEvent event) {
        // context is de-serialized as HashMap
        CallbackContext callbackContext = objectMapper.convertValue(event.getContext(), CallbackContext.class);
        HeartbeatConfig heartbeatConfig = callbackContext.getHeartbeatConfig();
        // take over heartbeat not to fail in case of long-running result processing
        Optional<Future<?>> heartBeatSchedule;
        if (heartbeatConfig != null) {
            heartBeatSchedule = Optional.of(heartbeatSender.start(heartbeatConfig));
        } else {
            heartBeatSchedule = Optional.empty();
        }
        return doCompleted(event.getNewStatus(), event.getOutputChecksum(), callbackContext)
                .handleAsync(Context.current().wrapFunction((nul, e) -> {
                    heartBeatSchedule.ifPresent(hb -> hb.cancel(false));
                    if (e != null) {
                        throw new CompletionException("Failed to process completion.", e);
                    }
                    return null;
                }), executor);
    }

    private CompletableFuture<Void> doCompleted(Status status, String outputChecksum, CallbackContext callbackContext) {
        Request invokerCallback = callbackContext.getInvokerCallback();

        HttpClientConfiguration clientConfiguration = HttpClientConfiguration.newBuilder()
                .termBaseUrl(callbackContext.getEnvironmentBaseUrl())
                .requestHeaders(getRequestHeaders())
                .build();
        BuildAgentClient buildAgentClient;
        try {
            buildAgentClient = new BuildAgentHttpClient(httpClient, clientConfiguration);
        } catch (BuildAgentClientException e) {
            return CompletableFuture.failedFuture(e);
        }

        final boolean debugEnabled;
        CompletableFuture<String> optionallyEnableSsh;
        logger.info("Script completionNotifier completed with status {}.", status);
        if ((status == Status.FAILED || status == Status.SYSTEM_ERROR || status == Status.INTERRUPTED)
                && callbackContext.isEnableDebugOnFailure()) {
            debugEnabled = true;
            optionallyEnableSsh = buildAgentClient.executeAsync("/usr/local/bin/startSshd.sh");
        } else {
            debugEnabled = false;
            optionallyEnableSsh = CompletableFuture.completedFuture(null);
        }

        return optionallyEnableSsh.handle(Context.current().wrapFunction((completedBuild, throwable) -> {
            if (throwable != null) {
                logger.error("Completing with SYSTEM_ERROR.", throwable);
                return BuildCompleted.builder().throwable(throwable).buildStatus(ResultStatus.SYSTEM_ERROR).build();
            } else {
                return BuildCompleted.builder()
                        .outputChecksum(outputChecksum)
                        .debugEnabled(debugEnabled)
                        .buildStatus(TypeConverters.toResultStatus(status))
                        .build();
            }
        })).thenCompose(completedBuild -> notifyInvoker(completedBuild, invokerCallback));
    }

    @WithSpan()
    public CompletableFuture<HttpClient.Response> cancel(
            @SpanAttribute(value = "buildCancelRequest") BuildCancelRequest buildCancelRequest) {
        HttpClientConfiguration clientConfiguration = HttpClientConfiguration.newBuilder()
                .termBaseUrl(buildCancelRequest.getBuildEnvironmentBaseUrl())
                .requestHeaders(getRequestHeaders())
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
            String scmTag,
            String buildCommand) {
        Map<String, String> values = new HashMap<>();
        values.put("workingDirectory", workingDirectory);
        values.put("projectName", projectName);
        values.put("scmUrl", scmUrl);
        values.put("scmRevision", scmRevision);
        values.put("scmTag", scmTag);
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

        addMdcOtelHeader(headers);

        return headers;
    }

    private void headersFromMdc(List<Request.Header> headers, MDCHeaderKeys headerKey) {
        String mdcValue = MDC.get(headerKey.getMdcKey());
        if (!Strings.isEmpty(mdcValue)) {
            headers.add(new Request.Header(headerKey.getHeaderName(), mdcValue));
        }
    }

    private void addMdcOtelHeader(List<Request.Header> headers) {

        SpanContext spanContext = Span.current().getSpanContext();
        headers.add(new Request.Header(MDCHeaderKeys.TRACE_ID.getHeaderName(), spanContext.getTraceId()));
        logger.debug("Added header ('{}','{}') ", MDCHeaderKeys.TRACE_ID.getHeaderName(), spanContext.getTraceId());
        headers.add(new Request.Header(MDCHeaderKeys.SPAN_ID.getHeaderName(), spanContext.getSpanId()));
        logger.debug("Added header ('{}','{}') ", MDCHeaderKeys.SPAN_ID.getHeaderName(), spanContext.getSpanId());

        Map<String, String> otelHeaders = new HashMap<>();
        otelHeaders.putAll(OtelUtils.createTraceParentHeader(spanContext));
        otelHeaders.putAll(OtelUtils.createTraceStateHeader(spanContext));
        otelHeaders.forEach((key, value) -> {
            if (!Strings.isEmpty(value)) {
                headers.add(new Request.Header(key, value));
                logger.debug("Added header ('{}','{}') ", key, value);
            }
        });
    }
}
