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
import io.github.zzih.rudder.api.request.WorkflowCreateRequest;
import io.github.zzih.rudder.api.request.WorkflowRunRequest;
import io.github.zzih.rudder.api.response.WorkflowInstanceResponse;
import io.github.zzih.rudder.api.response.WorkflowResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.enums.TriggerType;
import io.github.zzih.rudder.service.coordination.lock.WorkflowEditLockService;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.service.workflow.WorkflowDefinitionService;
import io.github.zzih.rudder.service.workflow.WorkflowHashUtils;
import io.github.zzih.rudder.service.workflow.WorkflowInstanceService;
import io.github.zzih.rudder.service.workflow.WorkflowScheduleService;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowInstanceDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowScheduleDTO;
import io.github.zzih.rudder.service.workflow.executor.WorkflowExecutor;
import io.github.zzih.rudder.service.workflow.executor.varpool.VarPoolManager;
import io.github.zzih.rudder.version.api.model.DiffResult;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/workflow-definitions")
@RequiredArgsConstructor
@RequireRole(RoleType.VIEWER)
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowInstanceService workflowInstanceService;
    private final WorkflowScheduleService workflowScheduleService;
    private final WorkflowExecutor workflowExecutor;
    private final VersionService versionService;
    private final WorkflowEditLockService editLockService;

    @PostMapping
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.CREATE, resourceType = AuditResourceType.WORKFLOW_DEFINITION)
    public Result<WorkflowResponse> create(@PathVariable Long workspaceId,
                                           @PathVariable Long projectCode,
                                           @Valid @RequestBody WorkflowCreateRequest request) {
        WorkflowDefinitionDTO body = new WorkflowDefinitionDTO();
        body.setName(request.getName());
        body.setDescription(request.getDescription());
        WorkflowDefinitionDTO created = workflowDefinitionService.createDetail(workspaceId, projectCode, body);
        WorkflowScheduleDTO schedule = saveScheduleFromRequest(created.getCode(), request);
        return Result.ok(WorkflowResponse.from(created, schedule));
    }

    @GetMapping
    public PageResult<WorkflowResponse> list(@PathVariable Long workspaceId,
                                             @PathVariable Long projectCode,
                                             @RequestParam(required = false) String searchVal,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        IPage<WorkflowDefinitionDTO> page =
                workflowDefinitionService.pageByProjectCodeDetail(projectCode, searchVal, pageNum, pageSize);
        List<WorkflowResponse> vos = page.getRecords().stream()
                .map(wf -> WorkflowResponse.from(wf,
                        workflowScheduleService.getByWorkflowDefinitionCodeDetail(wf.getCode())))
                .toList();
        return PageResult.of(vos, page.getTotal(), pageNum, pageSize);
    }

    @GetMapping("/{code}")
    public Result<WorkflowResponse> getByCode(@PathVariable Long workspaceId,
                                              @PathVariable Long projectCode,
                                              @PathVariable Long code) {
        WorkflowDefinitionDTO wf = workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        WorkflowScheduleDTO schedule = workflowScheduleService.getByWorkflowDefinitionCodeDetail(wf.getCode());
        List<TaskDefinitionDTO> taskDefs = workflowDefinitionService.listTaskDefinitionDTOs(wf.getCode());
        String hash = WorkflowHashUtils.compute(wf, taskDefs);
        return Result.ok(WorkflowResponse.from(wf, schedule, taskDefs, hash));
    }

    @PutMapping("/{code}")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.UPDATE, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#code")
    public Result<WorkflowResponse> update(@PathVariable Long workspaceId,
                                           @PathVariable Long projectCode,
                                           @PathVariable Long code,
                                           @RequestBody WorkflowCreateRequest request) {
        WorkflowDefinitionDTO body = new WorkflowDefinitionDTO();
        body.setName(request.getName());
        body.setDescription(request.getDescription());
        body.setDagJson(request.getDagJson());
        if (request.getGlobalParams() != null) {
            body.setGlobalParams(JsonUtils.toJson(request.getGlobalParams()));
        }
        WorkflowDefinitionDTO updated = workflowDefinitionService.updateDetail(
                workspaceId, projectCode, code, body, request.getTaskDefinitions(), request.getExpectedHash());
        WorkflowScheduleDTO schedule = saveScheduleFromRequest(updated.getCode(), request);
        if (schedule == null) {
            schedule = workflowScheduleService.getByWorkflowDefinitionCodeDetail(updated.getCode());
        }
        List<TaskDefinitionDTO> taskDefs = workflowDefinitionService.listTaskDefinitionDTOs(updated.getCode());
        String hash = WorkflowHashUtils.compute(updated, taskDefs);
        return Result.ok(WorkflowResponse.from(updated, schedule, taskDefs, hash));
    }

    @GetMapping("/{code}/task-definitions")
    public Result<List<TaskDefinitionDTO>> listTaskDefinitions(@PathVariable Long workspaceId,
                                                               @PathVariable Long projectCode,
                                                               @PathVariable Long code) {
        WorkflowDefinitionDTO wf = workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        return Result.ok(workflowDefinitionService.listTaskDefinitionDTOs(wf.getCode()));
    }

    @DeleteMapping("/{code}")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.DELETE, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#code")
    public Result<Void> delete(@PathVariable Long workspaceId,
                               @PathVariable Long projectCode,
                               @PathVariable Long code) {
        workflowDefinitionService.delete(workspaceId, projectCode, code);
        return Result.ok();
    }

    @PostMapping("/{code}/run")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.RUN, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#code")
    public Result<WorkflowInstanceResponse> run(@PathVariable Long workspaceId,
                                                @PathVariable Long projectCode,
                                                @PathVariable Long code,
                                                @RequestBody(required = false) WorkflowRunRequest request) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        // UI 触发只发简单 {key:value},Worker 全链路按 List<Property> 走 — 入口处包装成 IN/VARCHAR
        var overrideParams = request != null ? request.getOverrideParams() : null;
        var runtimeProps = VarPoolManager.wrapAsProperties(overrideParams, Direct.IN);
        WorkflowInstanceDTO instance =
                workflowInstanceService.createInstanceDTO(code, TriggerType.MANUAL, runtimeProps);
        workflowExecutor.execute(instance.getId());
        return Result.ok(BeanConvertUtils.convert(instance, WorkflowInstanceResponse.class));
    }

    @GetMapping("/{code}/versions")
    public PageResult<VersionRecord> listVersions(@PathVariable Long workspaceId,
                                                  @PathVariable Long projectCode,
                                                  @PathVariable Long code,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        IPage<VersionRecord> page = versionService.page(ResourceType.WORKFLOW, code, pageNum, pageSize);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @PostMapping("/{code}/commit")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.COMMIT, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#code")
    public Result<Void> commitVersion(@PathVariable Long workspaceId,
                                      @PathVariable Long projectCode,
                                      @PathVariable Long code,
                                      @Valid @RequestBody ScriptCommitRequest request) {
        workflowDefinitionService.saveVersion(workspaceId, projectCode, code, request.getMessage());
        return Result.ok();
    }

    @PostMapping("/{code}/rollback/{versionId}")
    @RequireRole(RoleType.DEVELOPER)
    @AuditLog(module = AuditModule.WORKFLOW, action = AuditAction.ROLLBACK, resourceType = AuditResourceType.WORKFLOW_DEFINITION, resourceCode = "#code")
    public Result<WorkflowResponse> rollback(@PathVariable Long workspaceId,
                                             @PathVariable Long projectCode,
                                             @PathVariable Long code,
                                             @PathVariable Long versionId) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        VersionRecord version = versionService.getValidatedWithContent(versionId, ResourceType.WORKFLOW, code);
        WorkflowDefinitionDTO updated = workflowDefinitionService.rollbackDetail(workspaceId, projectCode, code,
                version.getContent(), version.getVersionNo());
        WorkflowScheduleDTO schedule = workflowScheduleService.getByWorkflowDefinitionCodeDetail(updated.getCode());
        return Result.ok(WorkflowResponse.from(updated, schedule));
    }

    @GetMapping("/{code}/versions/{versionId}")
    public Result<VersionRecord> getVersion(@PathVariable Long workspaceId,
                                            @PathVariable Long projectCode,
                                            @PathVariable Long code,
                                            @PathVariable Long versionId) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        return Result.ok(versionService.getValidatedWithContent(versionId, ResourceType.WORKFLOW, code));
    }

    @GetMapping("/{code}/versions/diff")
    public Result<DiffResult> diffVersions(@PathVariable Long workspaceId,
                                           @PathVariable Long projectCode,
                                           @PathVariable Long code,
                                           @RequestParam Long versionIdA,
                                           @RequestParam Long versionIdB) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        // 校验两条都属于该工作流(getValidated 内部走 dao,不会返回内容)
        versionService.getValidated(versionIdA, ResourceType.WORKFLOW, code);
        versionService.getValidated(versionIdB, ResourceType.WORKFLOW, code);
        return Result.ok(versionService.diff(versionIdA, versionIdB));
    }

    /**
     * 从请求字段中保存调度信息（如果存在调度相关字段）。
     * 返回保存后的调度对象，如果未提供调度字段则返回 null。
     */
    private WorkflowScheduleDTO saveScheduleFromRequest(Long workflowDefinitionCode, WorkflowCreateRequest request) {
        if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
            return null;
        }
        WorkflowScheduleDTO schedule = new WorkflowScheduleDTO();
        schedule.setCronExpression(request.getCronExpression());
        schedule.setTimezone(request.getTimezone());
        if (request.getStartTime() != null && !request.getStartTime().isBlank()) {
            schedule.setStartTime(LocalDateTime.parse(request.getStartTime().replace(" ", "T")));
        }
        if (request.getEndTime() != null && !request.getEndTime().isBlank()) {
            schedule.setEndTime(LocalDateTime.parse(request.getEndTime().replace(" ", "T")));
        }
        if (request.getScheduleStatus() != null) {
            schedule.setStatus(request.getScheduleStatus());
        }
        return workflowScheduleService.saveOrUpdateDetail(workflowDefinitionCode, schedule);
    }

    /** 切换调度上线/下线;返回切换后的状态。 */
    @PostMapping("/{code}/schedule/toggle")
    public Result<WorkflowScheduleDTO> toggleSchedule(@PathVariable Long workspaceId,
                                                      @PathVariable Long projectCode,
                                                      @PathVariable Long code) {
        return Result.ok(workflowScheduleService.toggleStatusDetail(code));
    }

    // ==================== 编辑锁(纯 UX,数据安全靠 service.update 的 contentHash 校验) ====================

    @GetMapping("/{code}/lock")
    public Result<WorkflowEditLockService.Holder> peekLock(@PathVariable Long workspaceId,
                                                           @PathVariable Long projectCode,
                                                           @PathVariable Long code) {
        return Result.ok(editLockService.peek(code).orElse(null));
    }

    @PostMapping("/{code}/lock")
    @RequireRole(RoleType.DEVELOPER)
    public Result<WorkflowEditLockService.Holder> acquireLock(@PathVariable Long workspaceId,
                                                              @PathVariable Long projectCode,
                                                              @PathVariable Long code) {
        workflowDefinitionService.getByCodeDetail(workspaceId, projectCode, code);
        Long userId = UserContext.getUserId();
        return Result.ok(editLockService.tryAcquire(code, userId, UserContext.get().getUsername()).orElse(null));
    }

    @PostMapping("/{code}/lock/heartbeat")
    @RequireRole(RoleType.DEVELOPER)
    public Result<Boolean> heartbeatLock(@PathVariable Long workspaceId,
                                         @PathVariable Long projectCode,
                                         @PathVariable Long code) {
        return Result.ok(editLockService.heartbeat(code, UserContext.getUserId()));
    }

    @DeleteMapping("/{code}/lock")
    @RequireRole(RoleType.DEVELOPER)
    public Result<Void> releaseLock(@PathVariable Long workspaceId,
                                    @PathVariable Long projectCode,
                                    @PathVariable Long code) {
        editLockService.release(code, UserContext.getUserId());
        return Result.ok();
    }
}
