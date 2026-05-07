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
 * 认证 / 鉴权失败的语义标记(401/403/未登录/角色不足)。
 *
 * <p>跟 {@link NotFoundException} 同理 — 仅作为读者直觉 + 日志分级标记,
 * 内部仍是 {@link BizException},只接受 {@link ErrorCode} + 可选 args。
 */
public class AuthException extends BizException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
