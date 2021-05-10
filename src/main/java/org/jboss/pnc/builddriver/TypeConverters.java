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

import org.jboss.pnc.api.enums.ResultStatus;
import org.jboss.pnc.buildagent.api.Status;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TypeConverters {
    public static ResultStatus toResultStatus(Status status) {
        switch (status) {
            case COMPLETED:
                return ResultStatus.SUCCESS;
            case FAILED:
                return ResultStatus.FAILED;
            case INTERRUPTED:
                return ResultStatus.CANCELLED;
            case SYSTEM_ERROR:
                return ResultStatus.SYSTEM_ERROR;
            default:
                throw new java.util.NoSuchElementException("Cannot convert " + status.name() + " to ResultStatus.");
        }
    }
}
