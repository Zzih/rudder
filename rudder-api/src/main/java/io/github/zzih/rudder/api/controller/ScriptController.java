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

import io.github.zzih.rudder.api.request.ScriptCommitRequest;
import io.github.zzih.rudder.api.request.ScriptCreateRequest;
import io.github.zzih.rudder.api.request.ScriptDispatchRequest;
import io.github.zzih.rudder.api.request.ScriptExecuteRequest;
import io.github.zzih.rudder.api.request.ScriptMoveRequest;
import io.github.zzih.rudder.api.response.ScriptBindingResponse;
import io.github.zzih.rudder.api.response.ScriptResponse;
import io.github.zzih.rudder.api.response.TaskInstanceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.api.service.ScriptBindingService;
import io.github.zzih.rudder.api.service.ScriptDispatchOrchestrator;
import io.github.zzih.rudder.api.service.ScriptVersionService;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.service.script.TaskInstanceService;
import io.github.zzih.rudder.service.script.dto.ScriptDTO;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/scripts")
@RequiredArgsConstructor
@RequireViewer
public class ScriptController {

    private final ScriptService scriptService;
    private final ScriptVersionService scriptVersionService;
    private final TaskInstanceService taskInstanceService;
    private final ScriptDispatchOrchestrator scriptDispatchOrchestrator;
    private final ScriptBindingService scriptBindingService;
    private final VersionService versionService;

    @PostMapping
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.CREATE, resourceType = AuditResourceType.SCRIPT)
    public Result<ScriptResponse> create(@PathVariable Long workspaceId,
                                         @Valid @RequestBody ScriptCreateRequest request) {
        ScriptDTO body = BeanConvertUtils.convert(request, ScriptDTO.class);
        return Result.ok(BeanConvertUtils.convert(scriptService.createDetail(workspaceId, body), ScriptResponse.class));
    }

    @GetMapping
    public Result<List<ScriptResponse>> list(@PathVariable Long workspaceId,
                                             @RequestParam(required = false) Long dirId) {
        if (dirId != null) {
            return Result
                    .ok(BeanConvertUtils.convertList(scriptService.listByDirIdDetail(dirId), ScriptResponse.class));
        }
        return Result.ok(
                BeanConvertUtils.convertList(scriptService.listByWorkspaceIdDetail(workspaceId), ScriptResponse.class));
    }

    @GetMapping("/{code}")
    public Result<ScriptResponse> getByCode(@PathVariable Long workspaceId,
                                            @PathVariable Long code) {
        return Result
                .ok(BeanConvertUtils.convert(scriptService.getByCodeDetail(workspaceId, code), ScriptResponse.class));
    }

    @GetMapping("/{code}/versions")
    public Result<IPage<VersionRecord>> listVersions(@PathVariable Long workspaceId,
                                                     @PathVariable Long code,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        scriptService.getByCodeDetail(workspaceId, code); // validate access
        return Result.ok(versionService.page(ResourceType.SCRIPT, code, pageNum, pageSize));
    }

    @PostMapping("/{code}/commit")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.COMMIT, resourceType = AuditResourceType.SCRIPT, resourceCode = "#code")
    public Result<Void> commitVersion(@PathVariable Long workspaceId,
                                      @PathVariable Long code,
                                      @Valid @RequestBody ScriptCommitRequest request) {
        scriptVersionService.saveVersion(workspaceId, code, request.getMessage());
        return Result.ok();
    }

    @GetMapping("/{code}/versions/{versionId}")
    public Result<VersionRecord> getVersion(@PathVariable Long workspaceId,
                                            @PathVariable Long code,
                                            @PathVariable Long versionId) {
        scriptService.getByCodeDetail(workspaceId, code); // validate access
        return Result.ok(versionService.getValidatedWithContent(versionId, ResourceType.SCRIPT, code));
    }

    @PostMapping("/{code}/rollback/{versionId}")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.ROLLBACK, resourceType = AuditResourceType.SCRIPT, resourceCode = "#code")
    public Result<ScriptResponse> rollback(@PathVariable Long workspaceId,
                                           @PathVariable Long code,
                                           @PathVariable Long versionId) {
        scriptService.getByCodeDetail(workspaceId, code); // validate access
        VersionRecord version = versionService.getValidatedWithContent(versionId, ResourceType.SCRIPT, code);
        return Result.ok(BeanConvertUtils.convert(
                scriptVersionService.rollback(workspaceId, code, version.getContent(), version.getVersionNo()),
                ScriptResponse.class));
    }

    @PutMapping("/{code}")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.UPDATE, resourceType = AuditResourceType.SCRIPT, resourceCode = "#code")
    public Result<ScriptResponse> update(@PathVariable Long workspaceId,
                                         @PathVariable Long code,
                                         @RequestBody ScriptCreateRequest request) {
        ScriptDTO body = BeanConvertUtils.convert(request, ScriptDTO.class);
        return Result.ok(BeanConvertUtils.convert(
                scriptService.updateDetail(workspaceId, code, body), ScriptResponse.class));
    }

    @DeleteMapping("/{code}")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.DELETE, resourceType = AuditResourceType.SCRIPT, resourceCode = "#code")
    public Result<Void> delete(@PathVariable Long workspaceId,
                               @PathVariable Long code) {
        scriptService.delete(workspaceId, code);
        return Result.ok();
    }

    @PostMapping("/{code}/move")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.UPDATE, resourceType = AuditResourceType.SCRIPT, description = "移动脚本", resourceCode = "#code")
    public Result<ScriptResponse> move(@PathVariable Long workspaceId,
                                       @PathVariable Long code,
                                       @RequestBody ScriptMoveRequest request) {
        return Result.ok(BeanConvertUtils.convert(
                scriptService.moveDetail(workspaceId, code, request.getDirId()), ScriptResponse.class));
    }

    @PostMapping("/{code}/execute")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.EXECUTE, resourceType = AuditResourceType.SCRIPT, resourceCode = "#code")
    public Result<TaskInstanceResponse> execute(@PathVariable Long workspaceId,
                                                @PathVariable Long code,
                                                @Valid @RequestBody ScriptExecuteRequest request) {
        scriptService.getByCodeDetail(workspaceId, code); // validate access
        return Result.ok(BeanConvertUtils.convert(
                taskInstanceService.executeDetail(code, request.getDatasourceId(), request.getSql(),
                        request.getExecutionMode(), request.getParams()),
                TaskInstanceResponse.class));
    }

    @GetMapping("/{code}/binding")
    public Result<ScriptBindingResponse> getBinding(@PathVariable Long workspaceId,
                                                    @PathVariable Long code) {
        return Result.ok(scriptBindingService.getBinding(workspaceId, code));
    }

    @PostMapping("/{code}/push")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.PUSH, resourceType = AuditResourceType.SCRIPT, description = "推送到工作流", resourceCode = "#code")
    public Result<ScriptBindingResponse> push(@PathVariable Long workspaceId,
                                              @PathVariable Long code,
                                              @RequestParam Long projectCode,
                                              @Valid @RequestBody ScriptDispatchRequest request) {
        return Result.ok(scriptBindingService.push(workspaceId, code, projectCode, request));
    }

    @PostMapping("/{code}/dispatch")
    @RequireDeveloper
    @AuditLog(module = AuditModule.SCRIPT, action = AuditAction.DISPATCH, resourceType = AuditResourceType.SCRIPT, description = "脚本派发成 DAG 节点", resourceCode = "#code")
    public Result<Void> dispatch(@PathVariable Long workspaceId,
                                 @RequestParam Long projectCode,
                                 @PathVariable Long code,
                                 @Valid @RequestBody ScriptDispatchRequest request) {
        scriptDispatchOrchestrator.dispatchToWorkflow(workspaceId, projectCode, code,
                request.getWorkflowDefinitionCode());
        return Result.ok();
    }
}
