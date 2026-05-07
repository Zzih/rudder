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
public enum ApprovalErrorCode implements ErrorCode {

    APPROVAL_NOT_FOUND(5001, "err.ApprovalErrorCode.APPROVAL_NOT_FOUND"),
    APPROVAL_ALREADY_RESOLVED(5002, "err.ApprovalErrorCode.APPROVAL_ALREADY_RESOLVED"),
    APPROVAL_PERMISSION_DENIED(5003, "err.ApprovalErrorCode.APPROVAL_PERMISSION_DENIED"),
    REJECT_REASON_REQUIRED(5004, "err.ApprovalErrorCode.REJECT_REASON_REQUIRED");

    private final int code;
    private final String message;
}
