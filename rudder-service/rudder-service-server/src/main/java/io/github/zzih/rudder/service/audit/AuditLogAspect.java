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

package io.github.zzih.rudder.service.audit;

import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditLogRecord;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.bean.AspectArgUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 复用 Spring 自身 {@link CachedExpressionEvaluator} 基础设施(和 {@code @Cacheable} / {@code @EventListener(condition=...)} 同款):
 * 表达式按 {@link AnnotatedElementKey} 缓存,上下文用 {@link MethodBasedEvaluationContext} 自动绑定
 * {@code #paramName} / {@code #a0..#aN} / {@code #root.method} 等。
 *
 * <p>住 server 模块:它切的是 controller 上的 {@code @AuditLog},execution 没有 controller,无需此 aspect。
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect extends CachedExpressionEvaluator {

    private static final Object NO_ROOT = new Object();

    private final AuditLogAsyncService auditLogAsyncService;

    /** Expression 缓存;父类 {@code getExpression} 用内嵌 {@code ExpressionKey} 作为复合 key。 */
    private final Map<CachedExpressionEvaluator.ExpressionKey, Expression> expressionCache = new ConcurrentHashMap<>();

    public AuditLogAspect(AuditLogAsyncService auditLogAsyncService) {
        this.auditLogAsyncService = auditLogAsyncService;
    }

    @Around("@annotation(io.github.zzih.rudder.common.audit.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        long start = System.currentTimeMillis();
        Throwable thrown = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            try {
                UserContext.UserInfo user = UserContext.get();
                HttpServletRequest request = AspectArgUtils.currentRequest();
                String errorMessage = thrown == null ? null
                        : StringUtils.abbreviate(thrown.getClass().getSimpleName() + ": " + thrown.getMessage(),
                                AuditLogRecord.MAX_ERROR_MESSAGE_LEN);
                Long resourceCode = resolveResourceCode(auditLog, method, joinPoint.getArgs(), result, thrown);
                auditLogAsyncService.saveAuditLog(AuditLogRecord.from(
                        auditLog, user, request, resourceCode,
                        AspectArgUtils.buildSafeArgsJson(joinPoint, AuditLogRecord.MAX_REQUEST_PARAMS_LEN),
                        thrown, errorMessage,
                        System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.warn("Failed to build audit log record", e);
            }
        }
    }

    /**
     * 把 {@link AuditLog#resourceCode()} SpEL 表达式求值成 {@code Long},写入 {@code t_r_audit_log.resource_code}。
     *
     * <p>可用变量:方法参数按名({@code #id} / {@code #code} / {@code #request.subField})、
     * {@code #a0..#aN} / {@code #root.method} / {@code #root.args}、
     * 返回值 {@code #result}(仅业务方法成功返回后可用,抛异常时表达式引用 {@code #result} 会被短路为 null)。
     *
     * <p>表达式结果**必须可以转为 {@code Long}**;字符串 id 类资源目前不支持。
     * 求值失败(typo / 类型不匹配 / null 路径)→ 记 WARN 返回 null,不影响业务响应。
     */
    private Long resolveResourceCode(AuditLog auditLog, Method method, Object[] args, Object result, Throwable thrown) {
        String expr = auditLog.resourceCode();
        if (expr == null || expr.isEmpty()) {
            return null;
        }
        if (thrown != null && expr.contains("#result")) {
            return null;
        }
        try {
            Expression parsed = getExpression(expressionCache,
                    new AnnotatedElementKey(method, method.getDeclaringClass()), expr);
            EvaluationContext ctx = new MethodBasedEvaluationContext(
                    NO_ROOT, method, args, getParameterNameDiscoverer());
            ctx.setVariable("result", result);
            return parsed.getValue(ctx, Long.class);
        } catch (Exception e) {
            log.warn("Failed to resolve @AuditLog resourceCode [{}] on {} ({}): {}",
                    expr, method.getName(), e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
