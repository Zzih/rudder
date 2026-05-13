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

import io.github.zzih.rudder.api.request.AuthSourceCreateRequest;
import io.github.zzih.rudder.api.request.AuthSourceUpdateRequest;
import io.github.zzih.rudder.api.response.AuthSourceDetailResponse;
import io.github.zzih.rudder.api.response.AuthSourceSummaryResponse;
import io.github.zzih.rudder.api.security.annotation.RequireSuperAdmin;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.auth.AuthSourceService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Admin 端管理 auth source(SSO 登录方式)。 */
@RestController
@RequestMapping("/api/admin/auth-sources")
@RequiredArgsConstructor
@RequireSuperAdmin
public class AdminAuthSourceController {

    private final AuthSourceService authSourceService;

    @GetMapping
    public Result<List<AuthSourceSummaryResponse>> list() {
        List<AuthSourceSummaryResponse> list = authSourceService.listAllDetail().stream()
                .map(AuthSourceSummaryResponse::from)
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<AuthSourceDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(AuthSourceDetailResponse.from(authSourceService.getByIdDetail(id)));
    }

    @PostMapping
    @AuditLog(module = AuditModule.AUTH_SOURCE, action = AuditAction.CREATE, resourceType = AuditResourceType.AUTH_SOURCE, resourceCode = "#result.data.id", description = "创建认证源")
    public Result<AuthSourceDetailResponse> create(@Valid @RequestBody AuthSourceCreateRequest request) {
        return Result.ok(AuthSourceDetailResponse.from(authSourceService.createDetail(
                request.getName(),
                request.getType(),
                serializeConfig(request.getConfig()),
                request.getEnabled(),
                request.getPriority())));
    }

    @PutMapping("/{id}")
    @AuditLog(module = AuditModule.AUTH_SOURCE, action = AuditAction.UPDATE, resourceType = AuditResourceType.AUTH_SOURCE, resourceCode = "#id", description = "更新认证源")
    public Result<AuthSourceDetailResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody AuthSourceUpdateRequest request) {
        // request.config 为 null = patch 不改 config; request.type 仅 Jackson 多态用,service 忽略
        String configJson = request.getConfig() != null ? serializeConfig(request.getConfig()) : null;
        return Result.ok(AuthSourceDetailResponse.from(authSourceService.updateDetail(
                id,
                request.getName(),
                null, // type 在 updateDetail 里不是 patch 字段,service 内部从 existing 取
                configJson,
                request.getEnabled(),
                request.getPriority())));
    }

    /** 协议 config map → 明文 JSON 字符串,交给 service 加密落库。 */
    private static String serializeConfig(Map<String, Object> config) {
        return (config == null || config.isEmpty()) ? null : JsonUtils.toJson(config);
    }

    @DeleteMapping("/{id}")
    @AuditLog(module = AuditModule.AUTH_SOURCE, action = AuditAction.DELETE, resourceType = AuditResourceType.AUTH_SOURCE, resourceCode = "#id", description = "删除认证源")
    public Result<Void> delete(@PathVariable Long id) {
        authSourceService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/toggle")
    @AuditLog(module = AuditModule.AUTH_SOURCE, action = AuditAction.UPDATE, resourceType = AuditResourceType.AUTH_SOURCE, resourceCode = "#id", description = "启用/禁用认证源")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        authSourceService.toggleEnabled(id, enabled);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    @AuditLog(module = AuditModule.AUTH_SOURCE, action = AuditAction.TEST, resourceType = AuditResourceType.AUTH_SOURCE, resourceCode = "#id", description = "测试认证源连通性")
    public Result<HealthStatus> test(@PathVariable Long id) {
        return Result.ok(authSourceService.testConnection(id));
    }
}
