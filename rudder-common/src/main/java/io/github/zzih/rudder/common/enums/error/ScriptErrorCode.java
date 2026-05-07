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
public enum ScriptErrorCode implements ErrorCode {

    SCRIPT_NOT_FOUND(3001, "err.ScriptErrorCode.SCRIPT_NOT_FOUND"),
    SCRIPT_DIR_NOT_FOUND(3002, "err.ScriptErrorCode.SCRIPT_DIR_NOT_FOUND"),
    SCRIPT_NAME_EXISTS(3003, "err.ScriptErrorCode.SCRIPT_NAME_EXISTS"),
    EXECUTION_NOT_FOUND(3004, "err.ScriptErrorCode.EXECUTION_NOT_FOUND"),
    EXECUTION_FAILED(3005, "err.ScriptErrorCode.EXECUTION_FAILED"),
    SCRIPT_CONTENT_EMPTY(3006, "err.ScriptErrorCode.SCRIPT_CONTENT_EMPTY"),
    SCRIPT_DIR_MOVE_CYCLE(3007, "err.ScriptErrorCode.SCRIPT_DIR_MOVE_CYCLE"),
    SCRIPT_DIR_NAME_EXISTS(3008, "err.ScriptErrorCode.SCRIPT_DIR_NAME_EXISTS"),
    SCRIPT_BINDING_EXISTS(3010, "err.ScriptErrorCode.SCRIPT_BINDING_EXISTS"),
    SCRIPT_BINDING_FOREIGN(3011, "err.ScriptErrorCode.SCRIPT_BINDING_FOREIGN"),
    SCRIPT_BINDING_TARGET_NOT_FOUND(3012, "err.ScriptErrorCode.SCRIPT_BINDING_TARGET_NOT_FOUND"),
    TASK_INSTANCE_NO_ACCESS(3020, "err.ScriptErrorCode.TASK_INSTANCE_NO_ACCESS"),
    DISPATCH_NO_EXECUTION_NODE(3030, "err.ScriptErrorCode.DISPATCH_NO_EXECUTION_NODE"),
    DISPATCH_FAILED(3031, "err.ScriptErrorCode.DISPATCH_FAILED"),
    EXECUTION_RATE_LIMITED(3040, "err.ScriptErrorCode.EXECUTION_RATE_LIMITED");

    private final int code;
    private final String message;
}
