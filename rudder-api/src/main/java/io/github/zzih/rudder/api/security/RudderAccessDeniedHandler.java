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

package io.github.zzih.rudder.api.security;

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.result.ErrorCode;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 把 Spring {@link AccessDeniedException} 转回 Rudder {@link Result} JSON,
 * 按 {@link Authentication} 与失败的 {@code @PreAuthorize} 表达式推断具体错误码。
 */
@Component
public class RudderAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        ErrorCode error = pickErrorCode(ex);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<?> body = Result.fail(error.getCode(), I18n.t(error.getMessage()));
        response.getWriter().write(JsonUtils.toJson(body));
    }

    private ErrorCode pickErrorCode(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return WorkspaceErrorCode.USER_NOT_AUTHENTICATED;
        }
        if (auth.getAuthorities().isEmpty()) {
            return WorkspaceErrorCode.NO_WORKSPACE_ROLE;
        }
        return pickByExpression(ex);
    }

    /** 从 {@code @PreAuthorize} 失败的 SpEL 表达式中解析所需角色,匹配精确错误码。 */
    private ErrorCode pickByExpression(AccessDeniedException ex) {
        if (!(ex instanceof AuthorizationDeniedException denied)) {
            return WorkspaceErrorCode.INSUFFICIENT_PRIVILEGES;
        }
        AuthorizationResult result = denied.getAuthorizationResult();
        if (!(result instanceof ExpressionAuthorizationDecision decision)) {
            return WorkspaceErrorCode.INSUFFICIENT_PRIVILEGES;
        }
        String expr = decision.getExpression().getExpressionString();
        if (expr.contains("'SUPER_ADMIN'")) {
            return WorkspaceErrorCode.REQUIRES_SUPER_ADMIN;
        }
        if (expr.contains("'WORKSPACE_OWNER'")) {
            return WorkspaceErrorCode.REQUIRES_WORKSPACE_OWNER;
        }
        if (expr.contains("'DEVELOPER'")) {
            return WorkspaceErrorCode.REQUIRES_DEVELOPER;
        }
        return WorkspaceErrorCode.INSUFFICIENT_PRIVILEGES;
    }
}
