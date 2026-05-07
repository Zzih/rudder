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

package io.github.zzih.rudder.mcp.auth.dto;

import io.github.zzih.rudder.common.enums.mcp.McpTokenStatus;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Service 层 token 摘要 DTO —— 不含明文 / hash，字段镜像 entity，由 BeanConvert 自动映射。
 */
@Data
public class McpTokenSummary {

    private Long id;
    private Long userId;
    private Long workspaceId;
    private String workspaceName;
    private String name;
    private String description;
    private String tokenPrefix;
    private McpTokenStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
