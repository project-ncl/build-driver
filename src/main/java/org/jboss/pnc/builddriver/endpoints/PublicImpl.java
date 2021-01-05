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

import org.jboss.pnc.builddriver.Driver;
import org.jboss.pnc.builddriver.dto.BuildRequest;
import org.jboss.pnc.builddriver.dto.BuildResponse;
import org.jboss.pnc.builddriver.dto.CancelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.concurrent.CompletionStage;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class PublicImpl implements Public {

    private static final Logger logger = LoggerFactory.getLogger(PublicImpl.class);

    @Inject
    Driver driver;

    @Override
    public CompletionStage<BuildResponse> build(BuildRequest buildRequest) {
        logger.info("Requested project build: {}", buildRequest.getProjectName());
        return driver.start(buildRequest);
    }

    @Override
    public CompletionStage<Response> cancel(CancelRequest cancelRequest) {
        return driver.cancel(cancelRequest).thenApply((r) -> Response.status(r.getCode()).build());
    }
}
