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
 * {@link AuditLog#action()} 的受控取值。落库时使用 {@link #name()}。
 */
public enum AuditAction {
    // --- 通用 CRUD ---
    CREATE,
    UPDATE,
    DELETE,

    // --- 生命周期 ---
    EXECUTE,
    CANCEL,
    RUN,
    KILL,

    // --- 成员/权限 ---
    ADD_MEMBER,
    REMOVE_MEMBER,
    UPDATE_MEMBER_ROLE,
    UPDATE_OWNER,

    // --- 认证 ---
    LOGIN,
    LOGIN_LDAP,

    // --- 版本 ---
    COMMIT,
    ROLLBACK,

    // --- 审批 ---
    APPROVE,
    REJECT,
    WITHDRAW,
    CALLBACK,

    // --- 发布 ---
    EXECUTE_PUBLISH,
    PUBLISH_PROJECT,
    PUBLISH_WORKFLOW,

    // --- AI ---
    CHAT,
    AGENT_RUN,
    AGENT_STREAM,
    CREATE_SESSION,
    RENAME_SESSION,
    DELETE_SESSION,
    APPEND_MESSAGE,

    // --- 文件 ---
    UPLOAD,
    DOWNLOAD,
    MKDIR,
    RENAME,
    ONLINE_CREATE,
    UPDATE_CONTENT,

    // --- 数据源 ---
    TEST_CONNECTION,
    REFRESH_META_CACHE,

    // --- 任务 ---
    TRIGGER_SAVEPOINT,
    PUSH,
    DISPATCH,

    // --- 配置 / SPI ---
    VALIDATE,
    TEST,
    SEND_TEST,

    // --- 用户管理 ---
    RESET_PASSWORD,
    TOGGLE_SUPER_ADMIN,
    UPDATE_EMAIL
}
