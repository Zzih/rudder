/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.api.request;

import io.github.zzih.rudder.common.enums.execution.DispatchMode;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScriptDispatchRequest {

    @NotNull(message = "{validation.ScriptDispatchRequest.workflowDefinitionCode.required}")
    private Long workflowDefinitionCode;

    @NotNull(message = "{validation.ScriptDispatchRequest.mode.required}")
    private DispatchMode mode;

    /**
     * 替换模式下需要指定的目标任务定义 code。
     */
    private Long taskDefinitionCode;
}
