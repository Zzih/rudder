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

import io.github.zzih.rudder.api.request.DirectExecuteRequest;
import io.github.zzih.rudder.api.response.TaskInstanceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.common.execution.ResultResponse;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.service.coordination.ratelimit.RateLimitService;
import io.github.zzih.rudder.service.download.DownloadFormat;
import io.github.zzih.rudder.service.script.TaskInstanceService;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
@RequireViewer
public class ExecutionController {

    private static final int EXECUTE_MAX_PERMITS = 30;
    private static final Duration EXECUTE_WINDOW = Duration.ofMinutes(1);

    private final TaskInstanceService taskInstanceService;
    private final RateLimitService rateLimitService;

    @PostMapping("/direct")
    @RequireDeveloper
    @AuditLog(module = AuditModule.EXECUTION, action = AuditAction.EXECUTE, resourceType = AuditResourceType.TASK_INSTANCE)
    public Result<TaskInstanceResponse> executeDirect(
                                                      @Valid @RequestBody DirectExecuteRequest request) {
        enforceExecuteRateLimit();
        if (request.getWorkflowDefinitionCode() != null) {
            // 工作流编辑器内执行：创建 WorkflowInstance + TaskInstance
            return Result.ok(BeanConvertUtils.convert(
                    taskInstanceService.executeInWorkflowDefinitionDetail(
                            request.getWorkflowDefinitionCode(), request.getTaskDefinitionCode(),
                            request.getTaskType(), request.getDatasourceId(),
                            request.getSql(), request.getExecutionMode()),
                    TaskInstanceResponse.class));
        }
        Long workspaceId = UserContext.requireWorkspaceId();
        return Result.ok(BeanConvertUtils.convert(
                taskInstanceService.executeDirectDetail(
                        workspaceId, request.getTaskType(), request.getDatasourceId(), request.getSql(),
                        request.getExecutionMode()),
                TaskInstanceResponse.class));
    }

    @GetMapping("/{id}")
    public Result<TaskInstanceResponse> getById(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convert(taskInstanceService.getByIdDetail(id), TaskInstanceResponse.class));
    }

    @GetMapping("/script/{scriptCode}")
    public Result<List<TaskInstanceResponse>> listByScript(@PathVariable Long scriptCode) {
        return Result.ok(BeanConvertUtils.convertList(taskInstanceService.listByScriptCodeDetail(scriptCode),
                TaskInstanceResponse.class));
    }

    @PostMapping("/{id}/cancel")
    @RequireDeveloper
    @AuditLog(module = AuditModule.EXECUTION, action = AuditAction.CANCEL, resourceType = AuditResourceType.TASK_INSTANCE, resourceCode = "#id")
    public Result<Void> cancel(@PathVariable Long id) {
        taskInstanceService.cancel(id);
        return Result.ok();
    }

    /**
     * 增量获取任务实例的日志。
     *
     * @param offsetLine 已获取的行数（0 表示从头开始获取完整日志）
     */
    @GetMapping("/{id}/log")
    public Result<LogResponse> getLog(@PathVariable Long id,
                                      @RequestParam(defaultValue = "0") int offsetLine) {
        return Result.ok(taskInstanceService.getLog(id, offsetLine));
    }

    /**
     * 分页获取任务执行结果。
     */
    @GetMapping("/{id}/result")
    public Result<ResultResponse> getResult(@PathVariable Long id,
                                            @RequestParam(defaultValue = "0") int offset,
                                            @RequestParam(defaultValue = "500") int limit) {
        return Result.ok(taskInstanceService.getResult(id, offset, limit));
    }

    /** 下载任务结果。逐 batch 走 result fetch RPC,流式 transcode 到 HTTP 响应体。 */
    @GetMapping("/{id}/download")
    @AuditLog(module = AuditModule.EXECUTION, action = AuditAction.DOWNLOAD, resourceType = AuditResourceType.TASK_INSTANCE)
    public void download(@PathVariable Long id,
                         @RequestParam(defaultValue = "csv") String format,
                         HttpServletResponse response) throws IOException {
        DownloadFormat fmt = DownloadFormat.of(format);
        TaskInstanceService.DownloadHandle handle = taskInstanceService.prepareDownload(id);
        String filename = handle.filename() + fmt.extension();
        response.setContentType(fmt.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, HttpUtils.contentDispositionAttachment(filename));
        taskInstanceService.streamDownloadBody(handle, fmt, response.getOutputStream());
    }

    /**
     * 按当前用户 ID 限流直接执行，防止单用户任务洪水耗尽 Execution 节点。
     */
    private void enforceExecuteRateLimit() {
        Long userId = UserContext.getUserId();
        String key = userId != null ? String.valueOf(userId) : "anonymous";
        if (!rateLimitService.tryAcquire("execute", key, EXECUTE_MAX_PERMITS, EXECUTE_WINDOW)) {
            log.warn("Execute rate limit exceeded for user {}", key);
            throw new BizException(SystemErrorCode.TOO_MANY_REQUESTS,
                    "Too many executions, please wait before retrying");
        }
    }
}
