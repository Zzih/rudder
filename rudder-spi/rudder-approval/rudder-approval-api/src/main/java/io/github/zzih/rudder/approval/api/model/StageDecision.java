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

package io.github.zzih.rudder.approval.api.model;

import java.time.LocalDateTime;

/**
 * 外部渠道回传的单级决议：stage 为阶段标识（如 PROJECT_OWNER），approver 为该级审批人身份邮箱
 * （供上层按邮箱关联 Rudder 用户；渠道无邮箱时退化为显示名），decidedAt 为决议时间。
 */
public record StageDecision(String stage, String approver, LocalDateTime decidedAt, ApprovalAction action) {
}
