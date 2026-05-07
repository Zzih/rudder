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

package io.github.zzih.rudder.service.workflow.executor;

import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.registry.ServiceRegistryService;
import io.github.zzih.rudder.service.script.TaskDispatchService;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流孤儿回收器（仅 Server 节点启用）。
 *
 * <p>多 Server 部署下，若某 Server 崩溃，其内存中的 {@link WorkflowInstanceRunner} 消失，
 * 但 {@code t_r_workflow_instance.status=RUNNING} 的行仍留在 DB，无人推进 DAG。
 * 本组件两条路径兜底：
 * <ol>
 *   <li><b>启动自清</b>：Server 刚启动时，把所有 {@code owner_host=self} 的 RUNNING 行
 *       标 FAILED——这些一定是上一个进程残留（本进程还没执行任何 wf）。</li>
 *   <li><b>定时扫孤儿</b>：每 30s 扫一次 {@code owner_host NOT IN 在线 SERVER 列表} 的 RUNNING 行。
 *       优先 <b>真接管</b>（CAS owner_host 后 {@link WorkflowExecutor#resume} 继续推进 DAG），
 *       接管失败（另一 Server 已抢走 / 状态已变）再回落到标 FAILED。</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rudder.service.type", havingValue = "SERVER", matchIfMissing = true)
@RequiredArgsConstructor
public class WorkflowOrphanService {

    private final WorkflowInstanceDao workflowInstanceDao;
    private final TaskInstanceDao taskInstanceDao;
    private final TaskDispatchService taskDispatchService;
    private final ServiceRegistryService registryService;
    private final WorkflowExecutor workflowExecutor;

    /**
     * Server 启动完成后运行一次，清理上一个进程残留的 RUNNING wf。
     * <p>
     * 启动自清**不做接管**——刚启动的进程还没恢复运行时状态，直接继续推进上一次进程的 wf 不安全；
     * 这些 wf 由定时扫描路径处理（此时 owner_host 已不在线，会走 {@link #reapOrphans}）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startupSelfClean() {
        String self = registryService.getLocalRpcAddress();
        if (self == null || self.isBlank()) {
            return;
        }
        try {
            List<WorkflowInstance> stale = workflowInstanceDao.selectByOwnerHostAndStatus(
                    self, InstanceStatus.RUNNING);
            if (stale.isEmpty()) {
                return;
            }
            log.warn("Startup self-clean: {} stale RUNNING workflow(s) owned by self ({}), marking FAILED",
                    stale.size(), self);
            for (WorkflowInstance wf : stale) {
                finalizeOrphan(wf, "Server restarted before workflow finished");
            }
        } catch (Exception e) {
            log.error("Startup self-clean failed", e);
        }
    }

    /**
     * 每 30s 扫一次孤儿：owner_host 已不在线的 RUNNING wf。
     * 首次延迟 60s，避免 Server 冷启动时误杀尚未完成注册的兄弟节点拥有的 wf。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void reapOrphans() {
        String self = registryService.getLocalRpcAddress();
        try {
            List<WorkflowInstance> orphans = workflowInstanceDao.selectOrphanedRunning();
            if (orphans.isEmpty()) {
                return;
            }
            log.warn("Reaping {} orphan workflow instance(s)", orphans.size());
            for (WorkflowInstance wf : orphans) {
                if (self != null && !self.isBlank() && tryTakeover(wf, self)) {
                    continue;
                }
                finalizeOrphan(wf, "Owner server " + wf.getOwnerHost() + " is offline");
            }
        } catch (Exception e) {
            log.error("Orphan reaper error", e);
        }
    }

    /**
     * 尝试接管：CAS 抢占 owner_host，成功则重新从 DB 取最新实例（避免 status/varPool 脏读）再 resume。
     * 失败情形：另一 Server 已抢走 / wf 状态已变（成功/取消）；此时放弃接管。
     *
     * @return true 已成功接管并移交给 executor；false 未接管（调用方 fallback 到标 FAILED）
     */
    private boolean tryTakeover(WorkflowInstance wf, String self) {
        String oldOwner = wf.getOwnerHost();
        int affected = workflowInstanceDao.takeOverOrphan(wf.getId(), oldOwner, self);
        if (affected == 0) {
            log.info("Takeover skipped for wf {}: another Server or state change won the race", wf.getId());
            return false;
        }
        WorkflowInstance fresh = workflowInstanceDao.selectById(wf.getId());
        if (fresh == null || fresh.getStatus() != InstanceStatus.RUNNING) {
            log.info("Takeover skipped for wf {}: state changed to {} after CAS",
                    wf.getId(), fresh == null ? "DELETED" : fresh.getStatus());
            return false;
        }
        try {
            log.warn("Taking over orphan workflow {} from {} (new owner: {})", fresh.getId(), oldOwner, self);
            workflowExecutor.resume(fresh);
            return true;
        } catch (Exception e) {
            log.error("Resume threw for workflow {} after CAS; will mark FAILED under new owner {}",
                    fresh.getId(), self, e);
            finalizeOrphan(fresh, "Resume threw on new owner " + self + ": " + e.getMessage());
            return true;
        }
    }

    /**
     * 标记 wf 为 FAILED，并尽力取消其未完成的下游任务。
     */
    private void finalizeOrphan(WorkflowInstance wf, String reason) {
        try {
            wf.setStatus(InstanceStatus.FAILED);
            wf.setFinishedAt(LocalDateTime.now());
            workflowInstanceDao.updateById(wf);
            cancelRunningTasks(wf.getId(), reason);
            log.warn("Reaped workflow instance {}: {}", wf.getId(), reason);
        } catch (Exception e) {
            log.error("Failed to finalize orphan workflow {}", wf.getId(), e);
        }
    }

    private void cancelRunningTasks(Long workflowInstanceId, String reason) {
        int cancelled = taskInstanceDao.cancelPendingAndRunningByInstanceId(workflowInstanceId, reason);
        if (cancelled == 0) {
            return;
        }
        for (TaskInstance t : taskInstanceDao.selectByWorkflowInstanceId(workflowInstanceId)) {
            if (t.getStatus() != InstanceStatus.CANCELLED || t.getExecutionHost() == null) {
                continue;
            }
            try {
                taskDispatchService.cancelOnExecution(t.getExecutionHost(), t.getId());
            } catch (Exception e) {
                log.debug("Best-effort cancel RPC failed for task {}: {}", t.getId(), e.getMessage());
            }
        }
        log.warn("Reaped workflow {}: {} task(s) cancelled ({})", workflowInstanceId, cancelled, reason);
    }
}
