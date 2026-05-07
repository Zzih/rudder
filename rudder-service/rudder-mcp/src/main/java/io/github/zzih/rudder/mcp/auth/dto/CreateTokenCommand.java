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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service 层创建 MCP token 的入参。
 *
 * <p>{@code capabilityIds}:READ 直接置 ACTIVE,WRITE 落 PENDING_APPROVAL 并各自起一份审批单。
 */
public record CreateTokenCommand(
        Long userId,
        Long workspaceId,
        String name,
        String description,
        LocalDateTime expiresAt,
        List<String> capabilityIds) {
}
