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

package io.github.zzih.rudder.common.audit;

/**
 * {@link AuditLog#resourceType()} 的受控取值。
 *
 * <p>故意不复用 {@code common.enums.ResourceType}（那是 VersionStore 专用的 SCRIPT/WORKFLOW），
 * 审计维度更细（AI_SESSION / APPROVAL_RECORD / SPI_CONFIG 等）。
 *
 * <p>{@link #NONE} 用于不关联具体资源的动作（例如 LOGIN），落库时序列化成空字符串。
 */
public enum AuditResourceType {
    NONE,
    AI_SESSION,
    APPROVAL_RECORD,
    AUTH_SOURCE,
    DATASOURCE,
    FILE,
    NOTIFICATION_CONFIG,
    PROJECT,
    SCRIPT,
    SCRIPT_DIR,
    SPI_CONFIG,
    TASK_INSTANCE,
    USER,
    WORKFLOW_DEFINITION,
    WORKFLOW_INSTANCE,
    WORKSPACE
}
