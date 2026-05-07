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

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.net.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计日志一条记录的所有字段值对象。
 * 由 {@link AuditLogAspect} 构造，经 {@link AuditLogPersister} 写入 DB。
 *
 * <p>{@code status} 取值：{@code SUCCESS} / {@code FAILURE}。
 */
public record AuditLogRecord(
        Long userId,
        String username,
        String module,
        String action,
        String resourceType,
        Long resourceCode,
        String description,
        String requestIp,
        String requestMethod,
        String requestUri,
        String requestParams,
        String status,
        String errorMessage,
        Long durationMs) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";
    public static final String SYSTEM_USERNAME = "system";
    public static final String UNKNOWN_IP = "unknown";

    /** 对齐 {@code t_r_audit_log} schema 的列宽。改 schema 时同步改这里（反之亦然）。 */
    public static final int MAX_REQUEST_PARAMS_LEN = 2000; // column TEXT, 实际打点上限
    public static final int MAX_ERROR_MESSAGE_LEN = 512; // column VARCHAR(512)
    public static final int MAX_DESCRIPTION_LEN = 512; // column VARCHAR(512)

    /**
     * Build a record from the aspect-layer inputs. Keeps the positional constructor private-ish
     * by exposing a single factory that all call-site fields flow through.
     */
    public static AuditLogRecord from(AuditLog auditLog,
                                      UserContext.UserInfo user,
                                      HttpServletRequest request,
                                      Long resourceCode,
                                      String requestParams,
                                      Throwable thrown,
                                      String errorMessage,
                                      long durationMs) {
        AuditResourceType resourceType = auditLog.resourceType();
        return new AuditLogRecord(
                user != null ? user.getUserId() : 0L,
                user != null ? user.getUsername() : SYSTEM_USERNAME,
                auditLog.module().name(),
                auditLog.action().name(),
                resourceType == AuditResourceType.NONE ? null : resourceType.name(),
                resourceCode,
                auditLog.description(),
                request != null ? HttpUtils.resolveClientIp(request) : UNKNOWN_IP,
                request != null ? request.getMethod() : null,
                request != null ? request.getRequestURI() : null,
                requestParams,
                thrown == null ? STATUS_SUCCESS : STATUS_FAILURE,
                errorMessage,
                durationMs);
    }
}
