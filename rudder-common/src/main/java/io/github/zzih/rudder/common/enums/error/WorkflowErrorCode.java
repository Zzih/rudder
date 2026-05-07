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
public enum WorkflowErrorCode implements ErrorCode {

    WF_NOT_FOUND(4001, "err.WorkflowErrorCode.WF_NOT_FOUND"),
    WF_NAME_EXISTS(4002, "err.WorkflowErrorCode.WF_NAME_EXISTS"),
    WF_INSTANCE_NOT_FOUND(4003, "err.WorkflowErrorCode.WF_INSTANCE_NOT_FOUND"),
    WF_NOT_PUBLISHED(4004, "err.WorkflowErrorCode.WF_NOT_PUBLISHED"),
    WF_DAG_INVALID(4005, "err.WorkflowErrorCode.WF_DAG_INVALID"),
    WF_MAX_CONCURRENT(4006, "err.WorkflowErrorCode.WF_MAX_CONCURRENT"),
    PROJECT_NOT_FOUND(4007, "err.WorkflowErrorCode.PROJECT_NOT_FOUND"),
    PUBLISH_NOT_FOUND(4008, "err.WorkflowErrorCode.PUBLISH_NOT_FOUND"),
    PUBLISH_STATUS_INVALID(4009, "err.WorkflowErrorCode.PUBLISH_STATUS_INVALID"),
    PUBLISH_SERVICE_UNAVAILABLE(4010, "err.WorkflowErrorCode.PUBLISH_SERVICE_UNAVAILABLE"),
    PUBLISH_FAILED(4011, "err.WorkflowErrorCode.PUBLISH_FAILED"),
    WF_DAG_EMPTY(4012, "err.WorkflowErrorCode.WF_DAG_EMPTY"),
    VERSION_NOT_FOUND(4020, "err.WorkflowErrorCode.VERSION_NOT_FOUND");

    private final int code;
    private final String message;
}
