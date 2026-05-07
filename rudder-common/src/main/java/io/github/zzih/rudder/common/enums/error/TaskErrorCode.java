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
public enum TaskErrorCode implements ErrorCode {

    TASK_INIT_FAILED(7001, "err.TaskErrorCode.TASK_INIT_FAILED"),
    TASK_EXECUTE_FAILED(7002, "err.TaskErrorCode.TASK_EXECUTE_FAILED"),
    TASK_CANCEL_FAILED(7003, "err.TaskErrorCode.TASK_CANCEL_FAILED"),
    TASK_TIMEOUT(7004, "err.TaskErrorCode.TASK_TIMEOUT"),
    TASK_PARAM_INVALID(7005, "err.TaskErrorCode.TASK_PARAM_INVALID"),
    TASK_TYPE_NOT_FOUND(7006, "err.TaskErrorCode.TASK_TYPE_NOT_FOUND"),
    TASK_EXIT_CODE(7010, "err.TaskErrorCode.TASK_EXIT_CODE"),
    TASK_INTERRUPTED(7011, "err.TaskErrorCode.TASK_INTERRUPTED"),
    TASK_DEPS_UNSATISFIED(7012, "err.TaskErrorCode.TASK_DEPS_UNSATISFIED"),
    TASK_CANCELLED(7013, "err.TaskErrorCode.TASK_CANCELLED"),
    TASK_SHELL_TIMEOUT(7015, "err.TaskErrorCode.TASK_SHELL_TIMEOUT"),
    TASK_PYTHON_TIMEOUT(7016, "err.TaskErrorCode.TASK_PYTHON_TIMEOUT"),
    TASK_SEATUNNEL_TIMEOUT(7017, "err.TaskErrorCode.TASK_SEATUNNEL_TIMEOUT"),
    TASK_HTTP_URL_REQUIRED(7020, "err.TaskErrorCode.TASK_HTTP_URL_REQUIRED"),
    TASK_SEATUNNEL_CONFIG_EMPTY(7021, "err.TaskErrorCode.TASK_SEATUNNEL_CONFIG_EMPTY"),
    TASK_SUB_WORKFLOW_MISSING_CODE(7030, "err.TaskErrorCode.TASK_SUB_WORKFLOW_MISSING_CODE"),
    TASK_SUB_WORKFLOW_INTERRUPTED(7031, "err.TaskErrorCode.TASK_SUB_WORKFLOW_INTERRUPTED"),
    TASK_SUB_WORKFLOW_FAILED(7032, "err.TaskErrorCode.TASK_SUB_WORKFLOW_FAILED"),
    TASK_SWITCH_EVAL_FAILED(7040, "err.TaskErrorCode.TASK_SWITCH_EVAL_FAILED"),
    TASK_DEPENDENCY_CHECK_FAILED(7041, "err.TaskErrorCode.TASK_DEPENDENCY_CHECK_FAILED"),
    TASK_DEPENDENCY_TIMEOUT(7042, "err.TaskErrorCode.TASK_DEPENDENCY_TIMEOUT");

    private final int code;
    private final String message;
}
