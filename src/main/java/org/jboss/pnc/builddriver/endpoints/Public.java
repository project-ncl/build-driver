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

import org.jboss.pnc.builddriver.dto.BuildRequest;
import org.jboss.pnc.builddriver.dto.BuildResponse;
import org.jboss.pnc.builddriver.dto.CancelRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletionStage;

/**
 * Endpoint to start/cancel the build.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface Public {

    /**
     * Triggers the build execution for a given configuration.
     */
    @POST
    @Path("/build")
    CompletionStage<BuildResponse> build(BuildRequest buildRequest);

    /**
     * Cancel the build execution.
     */
    @PUT
    @Path("/cancel")
    CompletionStage<Response> cancel(CancelRequest cancelRequest);

}
