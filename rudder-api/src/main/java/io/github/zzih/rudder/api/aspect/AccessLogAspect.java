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

package io.github.zzih.rudder.api.aspect;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.bean.AspectArgUtils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class AccessLogAspect {

    private static final int MAX_ARGS_LEN = 1024;

    @Around("within(io.github.zzih.rudder.api.controller..*)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        HttpServletRequest request = AspectArgUtils.currentRequest();
        String uri = request != null ? request.getRequestURI() : "";
        String httpMethod = request != null ? request.getMethod() : "";

        String username = "anonymous";
        try {
            UserContext.UserInfo user = UserContext.get();
            if (user != null && user.getUsername() != null) {
                username = user.getUsername();
            }
        } catch (Exception ignored) {
        }

        String handler = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String args = AspectArgUtils.buildSafeArgsJson(joinPoint, MAX_ARGS_LEN);

        log.info("REQUEST  {} {} | user={} | handler={} | args={}", httpMethod, uri, username, handler, args);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            log.warn("RESPONSE {} {} | user={} | handler={} | cost={}ms | error={}",
                    httpMethod, uri, username, handler, cost, ex.getMessage());
            throw ex;
        }

        long cost = System.currentTimeMillis() - start;
        if (cost > 3000) {
            log.warn("RESPONSE {} {} | user={} | handler={} | cost={}ms [SLOW]", httpMethod, uri, username, handler,
                    cost);
        } else {
            log.info("RESPONSE {} {} | user={} | handler={} | cost={}ms", httpMethod, uri, username, handler, cost);
        }

        return result;
    }
}
