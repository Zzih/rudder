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

package io.github.zzih.rudder.api.interceptor;

import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // 异步 dispatch(SseEmitter / DeferredResult 完成后回调)会再跑一次 interceptor,
        // 但此时已经出了 TokenFilter 的线程,UserContext 为 null 会误伤。初始 REQUEST 阶段已鉴过权,直接放行。
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        // 1. 认证检查:必须已登录
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            throw new AuthException(WorkspaceErrorCode.USER_NOT_AUTHENTICATED);
        }

        // 2. 非 Controller 方法（静态资源等）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 3. 解析 @RequireRole 注解（方法级优先于类级）
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        // 4. 无注解 = 仅需认证，已通过
        if (requireRole == null) {
            return true;
        }

        // 5. 获取要求的最低角色（直接从枚举取，编译期安全）
        RoleType requiredRole = requireRole.value();

        // 6. SUPER_ADMIN 拥有最高权限，直接放行
        if (RoleType.SUPER_ADMIN.name().equals(userInfo.getRole())) {
            return true;
        }

        // 7. 要求 SUPER_ADMIN 但用户不是 → 拒绝
        if (requiredRole == RoleType.SUPER_ADMIN) {
            throw new AuthException(WorkspaceErrorCode.REQUIRES_SUPER_ADMIN);
        }

        // 8. 非 SUPER_ADMIN 的接口需要工作空间上下文
        if (userInfo.getRole() == null || userInfo.getRole().isBlank()) {
            throw new AuthException(WorkspaceErrorCode.NO_WORKSPACE_ROLE);
        }

        // 9. 比较角色层级
        RoleType userRole;
        try {
            userRole = RoleType.of(userInfo.getRole());
        } catch (IllegalArgumentException e) {
            throw new AuthException(WorkspaceErrorCode.INVALID_USER_ROLE, userInfo.getRole());
        }

        if (userRole.getLevel() < requiredRole.getLevel()) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Requires role " + requiredRole.name() + " or above, current: " + userRole.name());
        }

        return true;
    }
}
