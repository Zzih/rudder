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

package io.github.zzih.rudder.mcp.tool;

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.error.McpErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.mcp.audit.McpAuditWriter;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.ScopeChecker;
import io.github.zzih.rudder.mcp.auth.TokenView;
import io.github.zzih.rudder.mcp.http.McpRequestAttributes;
import io.github.zzih.rudder.service.coordination.ratelimit.RateLimitService;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 拦截所有同时带 {@link org.springframework.ai.mcp.annotation.McpTool} 和
 * {@link McpCapability} 的方法，统一做：
 *
 * <ol>
 *   <li>限流（每 token 配额，集群共享）</li>
 *   <li>双闸门：{@link ScopeChecker}（scope ∩ RBAC）</li>
 *   <li>审计日志（OK / DENIED_* / ERROR）</li>
 * </ol>
 *
 * <p>替代旧的 {@code McpToolDispatcher} —— 后者依赖泛型 {@code McpTool<I,O>} 接口、
 * 一个 tool 一个 Spring bean。aspect 让任意 {@code @McpTool} 方法直接获得这套保护，
 * 多个 tool 可以聚合在一个 {@code @Service} 类里。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class McpToolGuardAspect {

    /** 与 dispatcher 老路径同源；0 = 不限流。 */
    @Value("${rudder.mcp.rate-limit.per-minute:120}")
    private int rateLimitPerMinute;

    private final ScopeChecker scopeChecker;
    private final McpTokenService tokenService;
    private final McpAuditWriter auditWriter;
    private final RateLimitService rateLimitService;

    @Around("(@annotation(org.springframework.ai.mcp.annotation.McpTool) "
            + "|| @annotation(org.springframework.ai.mcp.annotation.McpResource)) "
            + "&& @annotation(io.github.zzih.rudder.mcp.tool.McpCapability)")
    public Object guard(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        McpTool toolAnn = method.getAnnotation(McpTool.class);
        McpResource resourceAnn = method.getAnnotation(McpResource.class);
        McpCapability capAnn = method.getAnnotation(McpCapability.class);
        String toolName;
        if (toolAnn != null) {
            toolName = toolAnn.name().isEmpty() ? method.getName() : toolAnn.name();
        } else {
            // resource 走的是 URI，审计名用 "resource:<uri>"
            toolName = "resource:" + resourceAnn.uri();
        }
        String capability = capAnn.value();

        TokenView tokenView = currentTokenView();
        long start = System.nanoTime();
        String inputJson = previewArgs(pjp.getArgs());

        // 0. 限流（先于授权检查，防暴力扫 capability）
        if (tokenView != null && rateLimitPerMinute > 0
                && !rateLimitService.tryAcquire(
                        "mcp:tool", tokenView.tokenId().toString(),
                        rateLimitPerMinute, Duration.ofMinutes(1))) {
            auditWriter.writeDenied(tokenView, toolName, capability,
                    "RATE_LIMITED", inputJson, elapsedMs(start));
            throw new BizException(McpErrorCode.TOKEN_RATE_LIMIT, rateLimitPerMinute + " req/min");
        }

        // 1. 双闸门
        RoleType role = tokenView == null
                ? null
                : tokenService.resolveRole(tokenView.userId(), tokenView.workspaceId());
        ScopeChecker.Decision decision = scopeChecker.check(tokenView, role, capability);
        if (decision != ScopeChecker.Decision.ALLOW) {
            auditWriter.writeDenied(tokenView, toolName, capability,
                    decision.name(), inputJson, elapsedMs(start));
            log.warn("MCP tool denied: tool={}, decision={}, tokenId={}, userId={}",
                    toolName, decision,
                    tokenView != null ? tokenView.tokenId() : null,
                    tokenView != null ? tokenView.userId() : null);
            throw switch (decision) {
                case DENIED_SCOPE -> new AuthException(McpErrorCode.DENIED_SCOPE, capability);
                case DENIED_RBAC -> new AuthException(McpErrorCode.DENIED_RBAC, capability);
                case DENIED_UNKNOWN_CAPABILITY -> new AuthException(McpErrorCode.DENIED_UNKNOWN_CAPABILITY, capability);
                default -> new AuthException(McpErrorCode.ACCESS_DENIED);
            };
        }

        // 2. 执行 + 审计
        try {
            Object output = pjp.proceed();
            auditWriter.writeOk(tokenView, toolName, capability, inputJson, elapsedMs(start));
            return output;
        } catch (Throwable e) {
            auditWriter.writeError(tokenView, toolName, capability, inputJson,
                    "EXECUTE_FAILED", e.getMessage(), elapsedMs(start));
            throw e;
        }
    }

    private static TokenView currentTokenView() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            Object v = req.getAttribute(McpRequestAttributes.TOKEN_VIEW);
            if (v instanceof TokenView tv) {
                return tv;
            }
        }
        return null;
    }

    /** 把方法实参拼成 JSON-ish 字符串，用于审计。失败返 null。 */
    private static String previewArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        try {
            return Arrays.stream(args)
                    .filter(a -> a != null && !isInjectedSpecial(a))
                    .map(JsonUtils::toJson)
                    .collect(Collectors.joining(","));
        } catch (Exception e) {
            return null;
        }
    }

    /** Spring AI 注入的特殊参数（exchange / meta / context）不进审计。 */
    private static boolean isInjectedSpecial(Object a) {
        String cn = a.getClass().getName();
        return cn.startsWith("io.modelcontextprotocol.")
                || cn.startsWith("org.springframework.ai.mcp.");
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
