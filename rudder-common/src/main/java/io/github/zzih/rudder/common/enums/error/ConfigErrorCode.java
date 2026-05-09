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

/** SPI 配置未就绪 / 不可用的统一错误码,占用 6000-6999。 */
@Getter
@AllArgsConstructor
public enum ConfigErrorCode implements ErrorCode {

    LLM_NOT_CONFIGURED(6001, "err.ConfigErrorCode.LLM_NOT_CONFIGURED"),
    EMBEDDING_NOT_CONFIGURED(6002, "err.ConfigErrorCode.EMBEDDING_NOT_CONFIGURED"),
    VECTOR_NOT_CONFIGURED(6003, "err.ConfigErrorCode.VECTOR_NOT_CONFIGURED"),
    FILE_NOT_CONFIGURED(6004, "err.ConfigErrorCode.FILE_NOT_CONFIGURED"),
    RESULT_NOT_CONFIGURED(6005, "err.ConfigErrorCode.RESULT_NOT_CONFIGURED"),
    RUNTIME_NOT_CONFIGURED(6006, "err.ConfigErrorCode.RUNTIME_NOT_CONFIGURED"),
    METADATA_NOT_CONFIGURED(6007, "err.ConfigErrorCode.METADATA_NOT_CONFIGURED"),
    APPROVAL_NOT_CONFIGURED(6008, "err.ConfigErrorCode.APPROVAL_NOT_CONFIGURED"),
    VERSION_NOT_CONFIGURED(6009, "err.ConfigErrorCode.VERSION_NOT_CONFIGURED"),
    RERANK_NOT_CONFIGURED(6012, "err.ConfigErrorCode.RERANK_NOT_CONFIGURED"),
    NOTIFICATION_NOT_CONFIGURED(6013, "err.ConfigErrorCode.NOTIFICATION_NOT_CONFIGURED"),
    PUBLISH_NOT_CONFIGURED(6014, "err.ConfigErrorCode.PUBLISH_NOT_CONFIGURED"),
    APPROVAL_CHANNEL_INACTIVE(6010, "err.ConfigErrorCode.APPROVAL_CHANNEL_INACTIVE"),
    APPROVAL_CALLBACK_FAILED(6011, "err.ConfigErrorCode.APPROVAL_CALLBACK_FAILED"),
    NOTIFICATION_CONFIG_NOT_FOUND(6020, "err.ConfigErrorCode.NOTIFICATION_CONFIG_NOT_FOUND"),
    UNKNOWN_PROVIDER_KIND(6030, "err.ConfigErrorCode.UNKNOWN_PROVIDER_KIND");

    private final int code;
    private final String message;
}
