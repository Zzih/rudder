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

/** AI / RAG / 方言相关错误码,占用 8000-8099。 */
@Getter
@AllArgsConstructor
public enum AiErrorCode implements ErrorCode {

    DOCUMENT_NOT_FOUND(8001, "err.AiErrorCode.DOCUMENT_NOT_FOUND"),
    DIALECT_TASKTYPE_UNSUPPORTED(8010, "err.AiErrorCode.DIALECT_TASKTYPE_UNSUPPORTED"),
    SKILL_REQUIRES_TOOL_NESTED(8020, "err.AiErrorCode.SKILL_REQUIRES_TOOL_NESTED"),
    SESSION_NOT_FOUND(8030, "err.AiErrorCode.SESSION_NOT_FOUND"),
    DOCUMENT_FILE_EMPTY_AFTER_PARSE(8031, "err.AiErrorCode.DOCUMENT_FILE_EMPTY_AFTER_PARSE");

    private final int code;
    private final String message;
}
