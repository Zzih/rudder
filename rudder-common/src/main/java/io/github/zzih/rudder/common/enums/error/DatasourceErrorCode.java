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

@Getter
@AllArgsConstructor
public enum DatasourceErrorCode implements ErrorCode {

    DS_NOT_FOUND(2001, "err.DatasourceErrorCode.DS_NOT_FOUND"),
    DS_NAME_EXISTS(2002, "err.DatasourceErrorCode.DS_NAME_EXISTS"),
    DS_CONNECTION_FAILED(2003, "err.DatasourceErrorCode.DS_CONNECTION_FAILED"),
    DS_NO_PERMISSION(2004, "err.DatasourceErrorCode.DS_NO_PERMISSION"),
    DS_POOL_ERROR(2005, "err.DatasourceErrorCode.DS_POOL_ERROR"),
    DS_NAME_IMMUTABLE(2006, "err.DatasourceErrorCode.DS_NAME_IMMUTABLE"),
    DS_CRED_ENCRYPT_FAILED(2007, "err.DatasourceErrorCode.DS_CRED_ENCRYPT_FAILED"),
    DS_CRED_DECRYPT_FAILED(2008, "err.DatasourceErrorCode.DS_CRED_DECRYPT_FAILED"),
    DS_PREVIEW_SQL_EMPTY(2009, "err.DatasourceErrorCode.DS_PREVIEW_SQL_EMPTY");

    private final int code;
    private final String message;
}
