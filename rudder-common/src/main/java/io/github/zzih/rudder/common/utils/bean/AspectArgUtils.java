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

package io.github.zzih.rudder.common.utils.bean;

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Shared helpers for AOP aspects that inspect controller method arguments.
 *
 * <p>封装 3 件事：安全序列化（过滤 Servlet 对象 / MultipartFile 只记文件名 / 敏感字段 mask）、
 * 拿当前 {@link HttpServletRequest}、超长截断。
 */
public final class AspectArgUtils {

    private static final Set<String> SENSITIVE_KEY_FRAGMENTS =
            Set.of("password", "secret", "token", "credential");

    private AspectArgUtils() {
    }

    /**
     * 取当前请求线程的 {@link HttpServletRequest}，取不到返回 null（非 HTTP 入口 / RequestContext 未绑定）。
     */
    public static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 把 JoinPoint 的参数序列化成 JSON 片段：Servlet 对象跳过、MultipartFile 只留文件名、
     * 参数名包含敏感关键字（password/secret/token/credential）置 {@code ***}；其余按 JSON 序列化。
     * 超过 {@code maxLen} 截断并追加 {@code ...}。
     *
     * <p>失败返回 {@code "[serialize error]"}，绝不抛出——调用方一般在 try/finally 里，
     * 异常会遮蔽真正的业务失败。
     */
    public static String buildSafeArgsJson(ProceedingJoinPoint joinPoint, int maxLen) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] names = sig.getParameterNames();
            Object[] values = joinPoint.getArgs();
            if (names == null || names.length == 0 || values == null) {
                return "{}";
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < names.length; i++) {
                Object v = values[i];
                if (v instanceof HttpServletRequest || v instanceof HttpServletResponse) {
                    continue;
                }
                if (v instanceof MultipartFile file) {
                    map.put(names[i], file.getOriginalFilename());
                    continue;
                }
                String lowerName = names[i].toLowerCase();
                if (SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(lowerName::contains)) {
                    map.put(names[i], "***");
                    continue;
                }
                map.put(names[i], v);
            }
            String json = JsonUtils.toJson(map);
            return StringUtils.abbreviate(json, maxLen);
        } catch (Exception e) {
            return "[serialize error]";
        }
    }
}
