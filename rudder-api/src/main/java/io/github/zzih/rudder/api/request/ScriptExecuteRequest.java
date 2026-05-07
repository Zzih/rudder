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

import java.util.Map;

import lombok.Data;

@Data
public class ScriptExecuteRequest {

    private Long datasourceId;

    /**
     * 可选：仅执行此 SQL，而非完整脚本内容。
     * 用于用户选中部分脚本运行的场景。
     */
    private String sql;

    /**
     * 执行模式：BATCH 或 STREAMING。若为 null，则使用脚本的默认设置。
     */
    private String executionMode;

    /**
     * 执行参数，用于替换脚本中的 ${varName} 占位符。
     */
    private Map<String, String> params;
}
