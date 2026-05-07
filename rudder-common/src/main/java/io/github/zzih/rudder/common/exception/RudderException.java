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

package io.github.zzih.rudder.common.exception;

import io.github.zzih.rudder.common.result.ErrorCode;

import java.text.MessageFormat;

import lombok.Getter;

/**
 * 平台异常基类。
 *
 * <h2>消息约定 — 全 ErrorCode 化</h2>
 *
 * <p>{@link ErrorCode#getMessage()} 是消息**模板**(可含 {@code {0},{1}} 占位),抛出端只传 {@code args}:
 * <pre>{@code
 *   throw new BizException(WorkspaceErrorCode.PROJECT_NOT_FOUND, projectCode);
 * }</pre>
 *
 * <p>Handler 出口先用 {@link I18n#t(String, Object...)} 把模板当 i18n key 解析(命中 bundle 则取本地化版本,
 * 否则 {@code useCodeAsDefaultMessage=true} 返回模板原文),再做 {@link MessageFormat#format} 占位填充。
 *
 * <p><b>禁用</b>:在 throw 端直接传字符串字面量 message。所有错误消息必须有对应的 {@link ErrorCode} 枚举条目。
 */
@Getter
public class RudderException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public RudderException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    public RudderException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    public RudderException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = null;
    }
}
