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

package io.github.zzih.rudder.common.enums.error;

import io.github.zzih.rudder.common.result.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** MCP PAT / scope / 工具守卫相关错误码,占用 9000-9099。 */
@Getter
@AllArgsConstructor
public enum McpErrorCode implements ErrorCode {

    TOKEN_NOT_FOUND(9001, "err.McpErrorCode.TOKEN_NOT_FOUND"),
    TOKEN_RATE_LIMIT(9002, "err.McpErrorCode.TOKEN_RATE_LIMIT"),
    ACCESS_DENIED(9003, "err.McpErrorCode.ACCESS_DENIED"),
    DENIED_SCOPE(9004, "err.McpErrorCode.DENIED_SCOPE"),
    DENIED_RBAC(9005, "err.McpErrorCode.DENIED_RBAC"),
    DENIED_UNKNOWN_CAPABILITY(9006, "err.McpErrorCode.DENIED_UNKNOWN_CAPABILITY"),
    ROLE_NOT_ALLOWED_FOR_CAPABILITY(9007, "err.McpErrorCode.ROLE_NOT_ALLOWED_FOR_CAPABILITY"),
    WORKSPACE_ID_REQUIRED(9010, "err.McpErrorCode.WORKSPACE_ID_REQUIRED"),
    EXPIRES_IN_DAYS_INVALID(9011, "err.McpErrorCode.EXPIRES_IN_DAYS_INVALID"),
    AT_LEAST_ONE_CAPABILITY_REQUIRED(9012, "err.McpErrorCode.AT_LEAST_ONE_CAPABILITY_REQUIRED");

    private final int code;
    private final String message;
}
