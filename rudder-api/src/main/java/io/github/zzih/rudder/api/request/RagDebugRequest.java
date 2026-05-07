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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** RAG debug 请求体。 */
@Data
public class RagDebugRequest {

    @NotBlank
    private String query;

    /** workspace 隔离用,留空走 admin 默认 workspace。 */
    private Long workspaceId;

    /** TaskType.name(),决定 engine 过滤(如 STARROCKS_SQL → 只看 STARROCKS engine 文档)。可选。 */
    private String taskType;
}
