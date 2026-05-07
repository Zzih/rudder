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
 * Task 执行层标记异常。
 *
 * <p>{@code Task} 接口签名 {@code throws TaskException},Task 实现里的
 * {@code catch (TaskException e) { throw e; }} 用来防止在外层 {@code catch (Exception e) {}}
 * 重新包装失败异常导致丢失原始 ErrorCode。
 *
 * <p>仅保留作为 catch 时的语义标记;消息一律走 {@link ErrorCode} 枚举,不接受字符串字面量。
 */
public class TaskException extends BizException {

    public TaskException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TaskException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public TaskException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
