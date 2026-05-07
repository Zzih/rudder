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

/** SPI provider 通用错误码,占用 9100-9199 段。 */
@Getter
@AllArgsConstructor
public enum SpiErrorCode implements ErrorCode {

    PROVIDER_NOT_FOUND(9101, "err.SpiErrorCode.PROVIDER_NOT_FOUND"),
    PROVIDER_CONFIG_INVALID(9102, "err.SpiErrorCode.PROVIDER_CONFIG_INVALID"),
    PROVIDER_EXECUTION_FAILED(9103, "err.SpiErrorCode.PROVIDER_EXECUTION_FAILED");

    private final int code;
    private final String message;
}
