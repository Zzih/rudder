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

package io.github.zzih.rudder.mcp.tool.datasource.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Data;

/**
 * datasource.test_connection 工具的返回值。
 *
 * <p>{@code ok=true} 表示连通；否则 {@code error} 给出底层异常的简短描述。
 */
@Data
public class TestConnectionResultDTO {

    @JsonPropertyDescription("Whether the datasource is reachable with the configured credentials")
    private boolean ok;

    @JsonPropertyDescription("Error message when ok=false; null when ok=true")
    private String error;
}
