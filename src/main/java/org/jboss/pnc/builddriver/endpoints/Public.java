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

package org.jboss.pnc.builddriver.endpoints;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.api.builddriver.dto.BuildCancelRequest;
import org.jboss.pnc.api.builddriver.dto.BuildRequest;
import org.jboss.pnc.api.builddriver.dto.BuildResponse;
import org.jboss.pnc.api.dto.ComponentVersion;
import org.jboss.pnc.builddriver.BuildInformationConstants;
import org.jboss.pnc.builddriver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletionStage;

/**
 * Endpoint to start/cancel the build.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Public {

    private static final Logger logger = LoggerFactory.getLogger(Public.class);

    @Inject
    Driver driver;

    @ConfigProperty(name = "quarkus.application.name")
    String name;

    /**
     * Triggers the build execution for a given configuration. Method returns when the build is running in a remote
     * build environment.
     */
    @RolesAllowed({ "pnc-app-build-driver-user", "pnc-users-admin" })
    @POST
    @Path("/build")
    public CompletionStage<BuildResponse> build(BuildRequest buildRequest) {
        logger.info("Requested project build: {}", buildRequest.getProjectName());
        return driver.start(buildRequest);
    }

    /**
     * Cancel the build execution.
     */
    @RolesAllowed({ "pnc-app-build-driver-user", "pnc-users-admin" })
    @PUT
    @Path("/cancel")
    public CompletionStage<Response> cancel(BuildCancelRequest buildCancelRequest) {
        logger.info("Requested cancel: {}", buildCancelRequest.getBuildExecutionId());
        return driver.cancel(buildCancelRequest).thenApply((r) -> Response.status(r.getCode()).build());
    }

    @Path("/version")
    @GET
    public ComponentVersion getVersion() {
        return ComponentVersion.builder()
                .name(name)
                .version(BuildInformationConstants.VERSION)
                .commit(BuildInformationConstants.COMMIT_HASH)
                .builtOn(ZonedDateTime.parse(BuildInformationConstants.BUILD_TIME))
                .build();
    }
}
