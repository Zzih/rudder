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

package io.github.zzih.rudder.mcp.audit;

import io.github.zzih.rudder.common.audit.AuditLogPersister;
import io.github.zzih.rudder.common.audit.AuditLogRecord;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.mcp.auth.TokenView;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 审计日志写入器 — 复用现有 {@code t_r_audit_log} 表，约定 {@code module=MCP}。
 *
 * <p>每次 tool 调用写一条记录（含 OK / DENIED_* / ERROR）。
 * 详见 docs/mcp-platform.md §3.2 审计字段约定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpAuditWriter {

    public static final String MODULE = "MCP";

    public static final String ACTION_TOOL_CALL = "TOOL_CALL";

    public static final String RESOURCE_TYPE_MCP_TOKEN = "MCP_TOKEN";

    private final AuditLogPersister persister;

    public void writeOk(TokenView token, String toolName, String capability,
                        String inputJson, long durationMs) {
        write(token, toolName, capability, inputJson, AuditLogRecord.STATUS_SUCCESS, null, null, durationMs);
    }

    public void writeDenied(TokenView token, String toolName, String capability,
                            String denyReason, String inputJson, long durationMs) {
        write(token, toolName, capability, inputJson, "DENIED",
                denyReason, "ACL gate: " + denyReason, durationMs);
    }

    public void writeError(TokenView token, String toolName, String capability,
                           String inputJson, String errorCode, String errorMessage, long durationMs) {
        write(token, toolName, capability, inputJson, AuditLogRecord.STATUS_FAILURE,
                errorCode, errorMessage, durationMs);
    }

    private void write(TokenView token, String toolName, String capability, String inputJson,
                       String status, String errorCode, String errorMessage, long durationMs) {
        try {
            HttpServletRequest req = currentRequest();
            String description = "tool=" + toolName + ", capability=" + capability
                    + (errorCode != null ? ", code=" + errorCode : "");
            description = truncate(description, AuditLogRecord.MAX_DESCRIPTION_LEN);
            String params = truncate(inputJson, AuditLogRecord.MAX_REQUEST_PARAMS_LEN);
            String err = truncate(errorMessage, AuditLogRecord.MAX_ERROR_MESSAGE_LEN);
            AuditLogRecord record = new AuditLogRecord(
                    token != null ? token.userId() : UserContext.getUserId(),
                    UserContext.getUsername(),
                    MODULE,
                    ACTION_TOOL_CALL,
                    RESOURCE_TYPE_MCP_TOKEN,
                    token != null ? token.tokenId() : null,
                    description,
                    req != null ? HttpUtils.resolveClientIp(req) : AuditLogRecord.UNKNOWN_IP,
                    req != null ? req.getMethod() : null,
                    req != null ? req.getRequestURI() : null,
                    params,
                    status,
                    err,
                    durationMs);
            persister.save(record);
        } catch (Exception e) {
            // 审计写入失败不影响主流程
            log.warn("MCP audit write failed (tool={}, status={}): {}",
                    toolName, status, e.getMessage());
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
