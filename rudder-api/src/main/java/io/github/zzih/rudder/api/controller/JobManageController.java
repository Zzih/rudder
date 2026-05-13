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

import io.github.zzih.rudder.api.response.TaskInstanceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.script.TaskInstanceService;
import io.github.zzih.rudder.service.script.dto.TaskInstanceDTO;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务管理 — 查看运行中任务、Kill、Savepoint 等运维操作。
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@RequireDeveloper
public class JobManageController {

    private final TaskInstanceService taskInstanceService;

    /**
     * 分页查询运行中/待执行的任务，支持按工作空间、名称、任务类型、运行平台筛选。
     */
    @GetMapping("/running")
    public PageResult<TaskInstanceResponse> listRunning(
                                                        @RequestParam(required = false) String name,
                                                        @RequestParam(required = false) String taskType,
                                                        @RequestParam(required = false) String runtimeType,
                                                        @RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        Long workspaceId = UserContext.requireWorkspaceId();
        IPage<TaskInstanceDTO> page =
                taskInstanceService.pageRunningDetail(workspaceId, name, taskType, runtimeType, pageNum, pageSize);
        return PageResult.of(
                BeanConvertUtils.convertList(page.getRecords(), TaskInstanceResponse.class),
                page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询指定脚本关联的运行中任务。
     */
    @GetMapping("/running/script/{scriptCode}")
    public Result<List<TaskInstanceResponse>> listRunningByScript(@PathVariable Long scriptCode) {
        return Result.ok(BeanConvertUtils.convertList(
                taskInstanceService.listRunningByScriptCodeDetail(scriptCode), TaskInstanceResponse.class));
    }

    /**
     * 查询任务详情（包含 runtimeType、appId、trackingUrl 等云端信息）。
     */
    @GetMapping("/{id}")
    public Result<TaskInstanceResponse> getDetail(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convert(
                taskInstanceService.getByIdDetail(id), TaskInstanceResponse.class));
    }

    /**
     * Kill 任务（通知 Execution 节点取消）。
     */
    @PostMapping("/{id}/kill")
    @AuditLog(module = AuditModule.JOB, action = AuditAction.KILL, resourceType = AuditResourceType.TASK_INSTANCE, description = "取消任务", resourceCode = "#id")
    public Result<Void> kill(@PathVariable Long id) {
        taskInstanceService.cancel(id);
        return Result.ok();
    }

    /**
     * 触发 Savepoint（仅 Flink streaming 任务）。
     */
    @PostMapping("/{id}/savepoint")
    @AuditLog(module = AuditModule.JOB, action = AuditAction.TRIGGER_SAVEPOINT, resourceType = AuditResourceType.TASK_INSTANCE, description = "触发 Flink Savepoint", resourceCode = "#id")
    public Result<String> triggerSavepoint(@PathVariable Long id) {
        String path = taskInstanceService.triggerSavepoint(id);
        return Result.ok(path);
    }
}
