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

package org.jboss.pnc.builddriver.runtime;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.common.concurrent.Sequence;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
@Slf4j
public class ApplicationLifecycle {

    @ConfigProperty(name = "sequenceGenerator.nodeId", defaultValue = "-1") // nodeId + nodeIdOffset must be < 1024
    int nodeId;

    @ConfigProperty(name = "sequenceGenerator.nodeIdOffset", defaultValue = "0") // nodeId + nodeIdOffset must be < 1024
    int nodeIdOffset;

    void onStart(@Observes StartupEvent event) {
        if (nodeId > -1) {
            Sequence.setNodeId(nodeIdOffset + nodeId);
        }

        // we need to log startup and shutdown events
        log.info("Application has started");
    }

    void onStop(@Observes ShutdownEvent event) {
        // we need to log startup and shutdown events
        log.info("The application is stopping");
    }
}
