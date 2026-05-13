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

import io.github.zzih.rudder.api.request.ScriptDirCreateRequest;
import io.github.zzih.rudder.api.request.ScriptDirMoveRequest;
import io.github.zzih.rudder.api.request.ScriptDirUpdateRequest;
import io.github.zzih.rudder.api.response.ScriptDirResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.script.ScriptDirService;
import io.github.zzih.rudder.service.script.dto.ScriptDirDTO;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/script-dirs")
@RequiredArgsConstructor
@RequireViewer
public class ScriptDirController {

    private final ScriptDirService scriptDirService;

    @PostMapping
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT_DIR, action = AuditAction.CREATE, resourceType = AuditResourceType.SCRIPT_DIR)
    public Result<ScriptDirResponse> create(@PathVariable Long workspaceId,
                                            @RequestBody ScriptDirCreateRequest request) {
        ScriptDirDTO body = BeanConvertUtils.convert(request, ScriptDirDTO.class);
        return Result.ok(BeanConvertUtils.convert(
                scriptDirService.createDetail(workspaceId, body), ScriptDirResponse.class));
    }

    @GetMapping
    public Result<List<ScriptDirResponse>> list(@PathVariable Long workspaceId) {
        return Result.ok(BeanConvertUtils.convertList(
                scriptDirService.listByWorkspaceIdDetail(workspaceId), ScriptDirResponse.class));
    }

    @PutMapping("/{id}")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT_DIR, action = AuditAction.UPDATE, resourceType = AuditResourceType.SCRIPT_DIR, resourceCode = "#id")
    public Result<ScriptDirResponse> update(@PathVariable Long workspaceId,
                                            @PathVariable Long id,
                                            @RequestBody ScriptDirUpdateRequest request) {
        ScriptDirDTO body = BeanConvertUtils.convert(request, ScriptDirDTO.class);
        return Result.ok(BeanConvertUtils.convert(
                scriptDirService.updateDetail(workspaceId, id, body), ScriptDirResponse.class));
    }

    @DeleteMapping("/{id}")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT_DIR, action = AuditAction.DELETE, resourceType = AuditResourceType.SCRIPT_DIR, resourceCode = "#id")
    public Result<Void> delete(@PathVariable Long workspaceId,
                               @PathVariable Long id) {
        scriptDirService.delete(workspaceId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/move")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT_DIR, action = AuditAction.UPDATE, resourceType = AuditResourceType.SCRIPT_DIR, description = "移动目录", resourceCode = "#id")
    public Result<ScriptDirResponse> move(@PathVariable Long workspaceId,
                                          @PathVariable Long id,
                                          @RequestBody ScriptDirMoveRequest request) {
        ScriptDirDTO body = BeanConvertUtils.convert(request, ScriptDirDTO.class);
        return Result.ok(BeanConvertUtils.convert(
                scriptDirService.moveDetail(workspaceId, id, body), ScriptDirResponse.class));
    }
}
