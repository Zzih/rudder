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

/** 数据权限申请相关错误码,占用 9300-9399。 */
@Getter
@AllArgsConstructor
public enum DataPermErrorCode implements ErrorCode {

    ROLE_NOT_FOUND(9301, "err.DataPermErrorCode.ROLE_NOT_FOUND"),
    ROLE_NAME_DUPLICATE(9302, "err.DataPermErrorCode.ROLE_NAME_DUPLICATE"),
    ROLE_IN_USE(9303, "err.DataPermErrorCode.ROLE_IN_USE"),

    APPLICATION_INVALID(9310, "err.DataPermErrorCode.APPLICATION_INVALID"),
    DATASOURCE_NOT_OPEN(9311, "err.DataPermErrorCode.DATASOURCE_NOT_OPEN"),
    RESOURCE_MISSING(9312, "err.DataPermErrorCode.RESOURCE_MISSING"),
    EXPIRE_DATE_INVALID(9313, "err.DataPermErrorCode.EXPIRE_DATE_INVALID"),

    GRANT_NOT_FOUND(9320, "err.DataPermErrorCode.GRANT_NOT_FOUND"),

    RANGER_UNREACHABLE(9340, "err.DataPermErrorCode.RANGER_UNREACHABLE"),
    RANGER_SERVICE_NOT_FOUND(9341, "err.DataPermErrorCode.RANGER_SERVICE_NOT_FOUND"),
    RANGER_POLICY_CONFLICT(9342, "err.DataPermErrorCode.RANGER_POLICY_CONFLICT"),
    RANGER_POLICY_RESOURCE_CONFLICT(9347, "err.DataPermErrorCode.RANGER_POLICY_RESOURCE_CONFLICT"),
    RANGER_POLICY_TAKEOVER_FAILED(9348, "err.DataPermErrorCode.RANGER_POLICY_TAKEOVER_FAILED"),
    USERNAME_NORMALIZE_COLLISION(9343, "err.DataPermErrorCode.USERNAME_NORMALIZE_COLLISION"),
    RANGER_SERVICE_NAME_DUPLICATE(9344, "err.DataPermErrorCode.RANGER_SERVICE_NAME_DUPLICATE"),
    RANGER_SERVICE_IN_USE(9345, "err.DataPermErrorCode.RANGER_SERVICE_IN_USE"),
    RANGER_SERVICE_METADATA_DS_MISSING(9346, "err.DataPermErrorCode.RANGER_SERVICE_METADATA_DS_MISSING"),
    RANGER_SERVICE_NAME_REQUIRED(9349, "err.DataPermErrorCode.RANGER_SERVICE_NAME_REQUIRED"),
    RANGER_ADMIN_URL_REQUIRED(9355, "err.DataPermErrorCode.RANGER_ADMIN_URL_REQUIRED"),
    AT_LEAST_ONE_MODE_REQUIRED(9352, "err.DataPermErrorCode.AT_LEAST_ONE_MODE_REQUIRED"),
    LOCAL_MODE_NO_MANAGED_TASK_TYPE(9353, "err.DataPermErrorCode.LOCAL_MODE_NO_MANAGED_TASK_TYPE"),
    MANAGED_TASK_TYPE_DUPLICATE(9356, "err.DataPermErrorCode.MANAGED_TASK_TYPE_DUPLICATE"),
    LOCAL_AUTH_DENIED(9354, "err.DataPermErrorCode.LOCAL_AUTH_DENIED"),
    LOCAL_AUTH_UNQUALIFIED_TABLE(9357, "err.DataPermErrorCode.LOCAL_AUTH_UNQUALIFIED_TABLE"),

    INTEGRATION_NOT_FOUND(9390, "err.DataPermErrorCode.INTEGRATION_NOT_FOUND");

    private final int code;
    private final String message;
}
