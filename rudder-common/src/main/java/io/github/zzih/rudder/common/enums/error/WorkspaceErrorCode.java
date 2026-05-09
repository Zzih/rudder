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

/** 工作空间 / 用户 / 成员 / 项目 / 认证源 / Auth & 权限,占用 1000-1999。 */
@Getter
@AllArgsConstructor
public enum WorkspaceErrorCode implements ErrorCode {

    WORKSPACE_NOT_FOUND(1001, "err.WorkspaceErrorCode.WORKSPACE_NOT_FOUND"),
    WORKSPACE_NAME_EXISTS(1002, "err.WorkspaceErrorCode.WORKSPACE_NAME_EXISTS"),
    WORKSPACE_NAME_INVALID(1003, "err.WorkspaceErrorCode.WORKSPACE_NAME_INVALID"),
    PROJECT_NOT_FOUND(1010, "err.WorkspaceErrorCode.PROJECT_NOT_FOUND"),
    PROJECT_NAME_EXISTS(1011, "err.WorkspaceErrorCode.PROJECT_NAME_EXISTS"),
    PROJECT_PERMISSION_DENIED(1012, "err.WorkspaceErrorCode.PROJECT_PERMISSION_DENIED"),
    USER_NOT_FOUND(1020, "err.WorkspaceErrorCode.USER_NOT_FOUND"),
    USER_EXISTS(1021, "err.WorkspaceErrorCode.USER_EXISTS"),
    MEMBER_EXISTS(1030, "err.WorkspaceErrorCode.MEMBER_EXISTS"),
    MEMBER_NOT_FOUND(1031, "err.WorkspaceErrorCode.MEMBER_NOT_FOUND"),
    INVALID_ROLE(1040, "err.WorkspaceErrorCode.INVALID_ROLE"),
    PASSWORD_ERROR(1050, "err.WorkspaceErrorCode.PASSWORD_ERROR"),
    INVALID_CREDENTIALS(1051, "err.WorkspaceErrorCode.INVALID_CREDENTIALS"),
    TOKEN_EXPIRED(1060, "err.WorkspaceErrorCode.TOKEN_EXPIRED"),
    SSO_PROVIDER_NOT_ENABLED(1070, "err.WorkspaceErrorCode.SSO_PROVIDER_NOT_ENABLED"),
    SSO_PROVIDER_NOT_SUPPORTED(1071, "err.WorkspaceErrorCode.SSO_PROVIDER_NOT_SUPPORTED"),
    SSO_AUTH_FAILED(1072, "err.WorkspaceErrorCode.SSO_AUTH_FAILED"),
    LDAP_USER_NOT_FOUND(1073, "err.WorkspaceErrorCode.LDAP_USER_NOT_FOUND"),
    AUTH_SOURCE_NOT_FOUND(1080, "err.WorkspaceErrorCode.AUTH_SOURCE_NOT_FOUND"),
    AUTH_SOURCE_NAME_EXISTS(1081, "err.WorkspaceErrorCode.AUTH_SOURCE_NAME_EXISTS"),
    AUTH_SOURCE_SYSTEM_IMMUTABLE(1082, "err.WorkspaceErrorCode.AUTH_SOURCE_SYSTEM_IMMUTABLE"),
    AUTH_SOURCE_DISABLED(1083, "err.WorkspaceErrorCode.AUTH_SOURCE_DISABLED"),
    AUTH_SOURCE_NAME_BLANK(1084, "err.WorkspaceErrorCode.AUTH_SOURCE_NAME_BLANK"),
    INVALID_TOKEN(1101, "err.WorkspaceErrorCode.INVALID_TOKEN"),
    USER_NOT_AUTHENTICATED(1102, "err.WorkspaceErrorCode.USER_NOT_AUTHENTICATED"),
    REQUIRES_SUPER_ADMIN(1103, "err.WorkspaceErrorCode.REQUIRES_SUPER_ADMIN"),
    NO_WORKSPACE_ROLE(1104, "err.WorkspaceErrorCode.NO_WORKSPACE_ROLE"),
    INVALID_USER_ROLE(1105, "err.WorkspaceErrorCode.INVALID_USER_ROLE"),
    NOT_WORKSPACE_MEMBER(1106, "err.WorkspaceErrorCode.NOT_WORKSPACE_MEMBER"),
    NO_DATASOURCE_PERMISSION(1107, "err.WorkspaceErrorCode.NO_DATASOURCE_PERMISSION"),
    NO_PROJECT_WORKSPACE_ACCESS(1108, "err.WorkspaceErrorCode.NO_PROJECT_WORKSPACE_ACCESS"),
    NO_SCRIPT_WORKSPACE_ACCESS(1109, "err.WorkspaceErrorCode.NO_SCRIPT_WORKSPACE_ACCESS"),
    NO_WORKFLOW_WORKSPACE_ACCESS(1110, "err.WorkspaceErrorCode.NO_WORKFLOW_WORKSPACE_ACCESS"),
    LOGIN_RATE_LIMITED(1120, "err.WorkspaceErrorCode.LOGIN_RATE_LIMITED");

    private final int code;
    private final String message;
}
