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

import org.jboss.pnc.buildagent.api.TaskStatusUpdateEvent;
import org.jboss.pnc.builddriver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;

/**
 * Endpoint to receive build result from the build agent.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Path("/internal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Internal {

    private static final Logger logger = LoggerFactory.getLogger(Internal.class);

    @Inject
    Driver driver;

    /**
     * Used by Build Agent to notify completion. Response to Build Agent is returned when the result is sent to the
     * invoker who started the build.
     *
     * @param updateEvent
     * @return
     */
    @RolesAllowed({ "pnc-app-build-driver-user", "pnc-users-admin" })
    @PUT
    @Path("/completed")
    public CompletionStage<Void> buildExecutionCompleted(TaskStatusUpdateEvent updateEvent) {
        logger.info("Build completed, taskId: {}; status: {}.", updateEvent.getTaskId(), updateEvent.getNewStatus());
        return driver.completed(updateEvent);
    }
}
