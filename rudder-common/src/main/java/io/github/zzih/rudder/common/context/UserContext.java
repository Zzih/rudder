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

package io.github.zzih.rudder.common.context;

import io.github.zzih.rudder.common.enums.auth.RoleType;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 唯一的请求上下文来源（ThreadLocal）。
 *
 * <p>承载当前请求的 user / workspace / role 等信息，由入口 filter 统一注入：
 * <ul>
 *   <li>HTTP: JwtToUserContextFilter 把 Spring Security 解析好的 Jwt + X-Workspace-Id header → set</li>
 *   <li>MCP:  PatAuthFilter 解析 PAT → set（workspace_id 来自 token 绑定）</li>
 *   <li>RPC:  RpcContextInterceptor 从消息头读 → set</li>
 * </ul>
 *
 * <p>Service 层 / Controller 应通过本类的 require* / get* 读取上下文，
 * 不再从 header / param 显式接收 workspaceId 等参数。
 *
 * <p>线程池场景请使用 {@link #wrap(Runnable)} / {@link #wrap(Callable)} 捕获并回放上下文，
 * 临时切换上下文使用 {@link #runWith(UserInfo, Runnable)}。
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static void set(UserInfo userInfo) {
        HOLDER.set(userInfo);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static boolean isPresent() {
        return HOLDER.get() != null;
    }

    public static Long getUserId() {
        UserInfo userInfo = HOLDER.get();
        return userInfo != null ? userInfo.getUserId() : null;
    }

    public static String getUsername() {
        UserInfo userInfo = HOLDER.get();
        return userInfo != null ? userInfo.getUsername() : null;
    }

    public static Long getWorkspaceId() {
        UserInfo userInfo = HOLDER.get();
        return userInfo != null ? userInfo.getWorkspaceId() : null;
    }

    public static String getRole() {
        UserInfo userInfo = HOLDER.get();
        return userInfo != null ? userInfo.getRole() : null;
    }

    public static Long getProjectCode() {
        UserInfo userInfo = HOLDER.get();
        return userInfo != null ? userInfo.getProjectCode() : null;
    }

    /** 当前用户是否 SUPER_ADMIN。统一替代 {@code RoleType.SUPER_ADMIN.name().equals(user.getRole())} 的散落写法。 */
    public static boolean isSuperAdmin() {
        return RoleType.SUPER_ADMIN.name().equals(getRole());
    }

    /**
     * SUPER_ADMIN 返 null,其他角色返 {@link #getWorkspaceId()}。
     * 适用于"运维型"端点(test/meta/refresh-cache 等)需让 SUPER_ADMIN 跨工作空间执行的场景;
     * 列表/可见性接口请直接走 query 参数 + {@link #getWorkspaceId()},不要用本方法。
     */
    public static Long getWorkspaceIdOrNull() {
        return isSuperAdmin() ? null : getWorkspaceId();
    }

    /**
     * Fail-fast 读取 userId，未设置时抛 {@link IllegalStateException}。
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new IllegalStateException("UserContext.userId not set — request not authenticated");
        }
        return userId;
    }

    /**
     * Fail-fast 读取 workspaceId。Controller / Service 应优先使用此方法，
     * 避免静默 null 导致越权或 NPE。
     */
    public static Long requireWorkspaceId() {
        Long workspaceId = getWorkspaceId();
        if (workspaceId == null) {
            throw new IllegalStateException(
                    "UserContext.workspaceId not set — missing X-Workspace-Id header or token binding");
        }
        return workspaceId;
    }

    public static String requireUsername() {
        String username = getUsername();
        if (username == null) {
            throw new IllegalStateException("UserContext.username not set — request not authenticated");
        }
        return username;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 临时设置上下文执行 task，无论 task 是否抛异常都恢复原上下文。
     * 适合 MCP / 异步任务场景。
     */
    public static void runWith(UserInfo userInfo, Runnable task) {
        UserInfo previous = HOLDER.get();
        HOLDER.set(userInfo);
        try {
            task.run();
        } finally {
            if (previous != null) {
                HOLDER.set(previous);
            } else {
                HOLDER.remove();
            }
        }
    }

    public static <T> T runWith(UserInfo userInfo, Supplier<T> task) {
        UserInfo previous = HOLDER.get();
        HOLDER.set(userInfo);
        try {
            return task.get();
        } finally {
            if (previous != null) {
                HOLDER.set(previous);
            } else {
                HOLDER.remove();
            }
        }
    }

    public static <T> T callWith(UserInfo userInfo, Callable<T> task) throws Exception {
        UserInfo previous = HOLDER.get();
        HOLDER.set(userInfo);
        try {
            return task.call();
        } finally {
            if (previous != null) {
                HOLDER.set(previous);
            } else {
                HOLDER.remove();
            }
        }
    }

    /**
     * 捕获当前线程上下文，包装 Runnable 供线程池执行。
     * 执行时把上下文注入工作线程，结束自动 clear，避免污染线程池。
     */
    public static Runnable wrap(Runnable task) {
        UserInfo captured = HOLDER.get();
        if (captured == null) {
            return task;
        }
        return () -> runWith(captured, task);
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        UserInfo captured = HOLDER.get();
        if (captured == null) {
            return task;
        }
        return () -> callWith(captured, task);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInfo {

        private Long userId;
        private String username;
        private Long workspaceId;
        private Long projectCode;
        private String role;
    }
}
