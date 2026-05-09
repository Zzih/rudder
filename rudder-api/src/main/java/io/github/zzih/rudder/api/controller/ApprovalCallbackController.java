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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.model.ApprovalCallbackResult;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.service.config.ApprovalConfigService;
import io.github.zzih.rudder.service.workflow.ApprovalService;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalCallbackController {

    private final ApprovalConfigService approvalConfigService;
    private final ApprovalService approvalService;

    @PostMapping("/callback/{channel}")
    @AuditLog(module = AuditModule.APPROVAL, action = AuditAction.CALLBACK, resourceType = AuditResourceType.APPROVAL_RECORD, description = "外部审批系统回调")
    public ResponseEntity<Object> callback(@PathVariable String channel,
                                           @RequestBody String rawBody,
                                           HttpServletRequest request) {
        log.info("Received approval callback for channel: {}", channel);

        Map<String, String> headers = extractHeaders(request);

        String activeProvider = approvalConfigService.activeProvider();
        if (!channel.equalsIgnoreCase(activeProvider)) {
            log.warn("Rejecting callback for channel={} (platform active={}); approval system has been switched",
                    channel, activeProvider);
            return ResponseEntity.status(HttpStatus.GONE).body(Result.fail(ConfigErrorCode.APPROVAL_CHANNEL_INACTIVE));
        }
        ApprovalNotifier notifier = approvalConfigService.required();
        ApprovalCallbackResult result = notifier.handleCallback(rawBody, headers);

        if (result.hasDirectResponse()) {
            return ResponseEntity.ok(result.getDirectResponse());
        }

        if (result.hasCallback()) {
            try {
                approvalService.resolveFromCallback(result.getCallback());
            } catch (Exception e) {
                log.error("Failed to process approval callback for channel: {}", channel, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.fail(ConfigErrorCode.APPROVAL_CALLBACK_FAILED));
            }
        }

        return ResponseEntity.ok(Result.ok());
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
