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

package org.jboss.pnc.builddriver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ToString
@AllArgsConstructor
@Data
@Builder(builderClassName = "Builder", toBuilder = true)
@JsonDeserialize(builder = BuildCompleted.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuildCompleted {

    private final String buildLog;
    private final Status status;
    private final String outputChecksum;
    private final boolean debugEnabled;
    private final Throwable throwable;

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
    }

}
