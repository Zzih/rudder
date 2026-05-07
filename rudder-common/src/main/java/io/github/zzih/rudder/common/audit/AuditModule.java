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
 * {@link AuditLog#module()} 的受控取值。落库时使用 {@link #name()}（{@code t_r_audit_log.module} 列）。
 *
 * <p>新增模块时在此追加一项，不要再用字符串字面量，防止 dashboard 按模块聚合时同一功能被拆。
 */
public enum AuditModule {
    AI,
    AI_CONFIG,
    APPROVAL,
    APPROVAL_CONFIG,
    AUTH,
    AUTH_SOURCE,
    DATASOURCE,
    EXECUTION,
    FILE,
    FILE_CONFIG,
    JOB,
    METADATA_CONFIG,
    NOTIFICATION_CONFIG,
    PROJECT,
    PUBLISH,
    RUNTIME_CONFIG,
    SCRIPT,
    SCRIPT_DIR,
    USER,
    VERSION_CONFIG,
    WORKFLOW,
    WORKFLOW_INSTANCE,
    WORKSPACE
}
