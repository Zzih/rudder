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

/**
 * 资源不存在的语义标记。{@code GlobalExceptionHandler} 可据此打 INFO 级日志(无堆栈),
 * 调用方读 {@code throw new NotFoundException(...)} 时一目了然。
 *
 * <p>不接收字符串字面量 message — 只能用 {@link ErrorCode} + 可选 args。
 */
public class NotFoundException extends BizException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
