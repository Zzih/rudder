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

package io.github.zzih.rudder.service.workflow.controlflow.dependent;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.dao.WorkflowInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.workflow.controlflow.AbstractControlFlowTask;
import io.github.zzih.rudder.service.workflow.controlflow.DependRelation;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * DEPENDENT 控制流任务。
 * <p>
 * 阻塞轮询检查外部工作流/任务的执行状态是否满足依赖条件。
 * 支持 AND / OR 组合、多种时间周期（hour / day / week / month）、
 * 以及失败策略（立即失败 / 等待超时）。
 */
@Slf4j
public class DependentTask extends AbstractControlFlowTask {

    private final String configJson;
    private final TaskInstanceDao taskInstanceDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final WorkflowInstanceDao workflowInstanceDao;

    public DependentTask(String configJson, TaskInstanceDao taskInstanceDao,
                         WorkflowDefinitionDao workflowDefinitionDao, WorkflowInstanceDao workflowInstanceDao) {
        this.configJson = configJson;
        this.taskInstanceDao = taskInstanceDao;
        this.workflowDefinitionDao = workflowDefinitionDao;
        this.workflowInstanceDao = workflowInstanceDao;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle() throws TaskException {
        try {
            Map<String, Object> config = parseConfig(configJson);
            Map<String, Object> dependence =
                    (Map<String, Object>) config.getOrDefault("dependence", Map.of());
            List<Map<String, Object>> dependTaskList =
                    (List<Map<String, Object>>) dependence.getOrDefault("dependTaskList", List.of());
            DependRelation outerRelation =
                    DependRelation.valueOf(dependence.getOrDefault("relation", "AND").toString());

            if (dependTaskList.isEmpty()) {
                log.info("[Dependent] No dependencies configured, marking as SUCCESS");
                status = TaskStatus.SUCCESS;
                return;
            }

            int checkInterval = dependence.get("checkInterval") != null
                    ? Math.max(1, Integer.parseInt(dependence.get("checkInterval").toString()))
                    : 10;
            FailurePolicy failurePolicy = FailurePolicy
                    .valueOf(dependence.getOrDefault("failurePolicy", "DEPENDENT_FAILURE_FAILURE").toString());
            int failureWaitingTime = dependence.get("failureWaitingTime") != null
                    ? Integer.parseInt(dependence.get("failureWaitingTime").toString())
                    : 1;

            long deadline = failurePolicy == FailurePolicy.DEPENDENT_FAILURE_WAITING
                    ? System.currentTimeMillis() + failureWaitingTime * 60 * 1000L
                    : 0;

            log.info(
                    "[Dependent] Start checking {} dependency group(s), outerRelation={}, failurePolicy={}, checkInterval={}s, waitingTime={}min",
                    dependTaskList.size(), outerRelation, failurePolicy, checkInterval, failureWaitingTime);

            int round = 0;
            while (true) {
                round++;
                if (status == TaskStatus.CANCELLED) {
                    log.info("[Dependent] Task cancelled, abort");
                    throw new TaskException(TaskErrorCode.TASK_CANCELLED);
                }

                boolean satisfied = checkDependencies(dependTaskList, outerRelation);
                if (satisfied) {
                    log.info("[Dependent] All dependencies satisfied after {} round(s), marking as SUCCESS", round);
                    status = TaskStatus.SUCCESS;
                    return;
                }

                if (failurePolicy == FailurePolicy.DEPENDENT_FAILURE_FAILURE) {
                    log.warn("[Dependent] Dependencies not satisfied, failurePolicy=FAILURE, marking as FAILED");
                    status = TaskStatus.FAILED;
                    throw new TaskException(TaskErrorCode.TASK_DEPS_UNSATISFIED);
                }
                if (System.currentTimeMillis() > deadline) {
                    log.warn(
                            "[Dependent] Dependencies not satisfied after {} round(s), timed out ({}min), marking as FAILED",
                            round, failureWaitingTime);
                    status = TaskStatus.FAILED;
                    throw new TaskException(TaskErrorCode.TASK_DEPENDENCY_TIMEOUT);
                }

                log.info("[Dependent] Round {} not satisfied, waiting {}s before next check...", round, checkInterval);
                Thread.sleep(checkInterval * 1000L);
            }
        } catch (TaskException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = TaskStatus.CANCELLED;
            throw new TaskException(TaskErrorCode.TASK_INTERRUPTED);
        } catch (Exception e) {
            status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_DEPENDENCY_CHECK_FAILED, e.getMessage());
        }
    }

    // ======================== 依赖检查 ========================

    @SuppressWarnings("unchecked")
    private boolean checkDependencies(List<Map<String, Object>> dependTaskList, DependRelation outerRelation) {
        List<Boolean> groupResults = new ArrayList<>();
        for (int gi = 0; gi < dependTaskList.size(); gi++) {
            Map<String, Object> group = dependTaskList.get(gi);
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) group.getOrDefault("dependItemList", List.of());
            DependRelation innerRelation = DependRelation.valueOf(group.getOrDefault("relation", "AND").toString());
            log.info("[Dependent] Group {}: {} item(s), innerRelation={}", gi, items.size(), innerRelation);

            List<Boolean> itemResults = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Long definitionCode = item.get("definitionCode") != null
                        ? Long.valueOf(item.get("definitionCode").toString())
                        : null;
                int depTaskCode = item.get("depTaskCode") != null
                        ? Integer.parseInt(item.get("depTaskCode").toString())
                        : 0;
                DependCycle cycle = DependCycle.of(item.getOrDefault("cycle", "day").toString());
                DateValue dateValue = DateValue.of(item.getOrDefault("dateValue", "today").toString());

                if (definitionCode == null) {
                    log.warn("[Dependent]   Item skipped: definitionCode is null");
                    itemResults.add(false);
                    continue;
                }
                WorkflowDefinition depWorkflow = workflowDefinitionDao.selectByCode(definitionCode);
                if (depWorkflow == null) {
                    log.warn("[Dependent]   Item skipped: workflow not found for definitionCode={}", definitionCode);
                    itemResults.add(false);
                    continue;
                }

                LocalDateTime[] range = resolveDependentTimeRange(cycle, dateValue);
                log.info(
                        "[Dependent]   Checking workflow '{}' (code={}), depTaskCode={}, cycle={}, dateValue={}, timeRange=[{}, {}]",
                        depWorkflow.getName(), depWorkflow.getCode(), depTaskCode, cycle, dateValue, range[0],
                        range[1]);

                WorkflowInstance depInstance = workflowInstanceDao
                        .selectLatestByWorkflowDefinitionCodeInTimeRange(depWorkflow.getCode(), range[0], range[1]);
                if (depInstance == null) {
                    log.info("[Dependent]   No workflow instance found in time range");
                    itemResults.add(false);
                    continue;
                }

                if (depTaskCode == 0) {
                    boolean ok = depInstance.getStatus() == InstanceStatus.SUCCESS;
                    log.info("[Dependent]   WorkflowInstance #{} status={}, satisfied={}", depInstance.getId(),
                            depInstance.getStatus(), ok);
                    itemResults.add(ok);
                } else {
                    TaskInstance depTask = taskInstanceDao
                            .selectLatestByWorkflowInstanceIdAndTaskDefinitionCode(depInstance.getId(),
                                    (long) depTaskCode);
                    boolean ok = depTask != null && depTask.getStatus() == InstanceStatus.SUCCESS;
                    log.info("[Dependent]   Task (code={}) in instance #{}: {}, satisfied={}",
                            depTaskCode, depInstance.getId(),
                            depTask != null ? "status=" + depTask.getStatus() : "not found", ok);
                    itemResults.add(ok);
                }
            }

            boolean groupResult = innerRelation == DependRelation.AND
                    ? itemResults.stream().allMatch(Boolean::booleanValue)
                    : itemResults.stream().anyMatch(Boolean::booleanValue);
            log.info("[Dependent] Group {} result: items={}, groupSatisfied={}", gi, itemResults, groupResult);
            groupResults.add(groupResult);
        }
        boolean result = groupResults.isEmpty() || (outerRelation == DependRelation.AND
                ? groupResults.stream().allMatch(Boolean::booleanValue)
                : groupResults.stream().anyMatch(Boolean::booleanValue));
        log.info("[Dependent] Overall: groups={}, outerRelation={}, satisfied={}", groupResults, outerRelation, result);
        return result;
    }

    // ======================== 时间范围解析 ========================

    private LocalDateTime[] resolveDependentTimeRange(DependCycle cycle, DateValue dateValue) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start, end = now;
        switch (cycle) {
            case HOUR -> {
                start = switch (dateValue) {
                    case CURRENT_HOUR -> now.withMinute(0).withSecond(0).withNano(0);
                    case LAST_1_HOUR -> now.minusHours(1).withMinute(0).withSecond(0).withNano(0);
                    case LAST_2_HOURS -> now.minusHours(2).withMinute(0).withSecond(0).withNano(0);
                    case LAST_3_HOURS -> now.minusHours(3).withMinute(0).withSecond(0).withNano(0);
                    case LAST_24_HOURS -> now.minusHours(24).withMinute(0).withSecond(0).withNano(0);
                    default -> now.withMinute(0).withSecond(0).withNano(0);
                };
            }
            case WEEK -> {
                LocalDate today = now.toLocalDate();
                LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
                switch (dateValue) {
                    case THIS_WEEK -> {
                        start = monday.atStartOfDay();
                        end = monday.plusWeeks(1).atStartOfDay();
                    }
                    case LAST_WEEK -> {
                        start = monday.minusWeeks(1).atStartOfDay();
                        end = monday.atStartOfDay();
                    }
                    case LAST_MONDAY -> {
                        LocalDate d = monday.minusWeeks(1);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_TUESDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(1);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_WEDNESDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(2);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_THURSDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(3);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_FRIDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(4);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_SATURDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(5);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_SUNDAY -> {
                        LocalDate d = monday.minusWeeks(1).plusDays(6);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    default -> {
                        start = monday.atStartOfDay();
                        end = monday.plusWeeks(1).atStartOfDay();
                    }
                }
            }
            case MONTH -> {
                LocalDate today = now.toLocalDate();
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                switch (dateValue) {
                    case THIS_MONTH -> {
                        start = firstOfMonth.atStartOfDay();
                        end = firstOfMonth.plusMonths(1).atStartOfDay();
                    }
                    case THIS_MONTH_BEGIN -> {
                        start = firstOfMonth.atStartOfDay();
                        end = firstOfMonth.plusDays(1).atStartOfDay();
                    }
                    case LAST_MONTH -> {
                        start = firstOfMonth.minusMonths(1).atStartOfDay();
                        end = firstOfMonth.atStartOfDay();
                    }
                    case LAST_MONTH_BEGIN -> {
                        LocalDate d = firstOfMonth.minusMonths(1);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    case LAST_MONTH_END -> {
                        LocalDate d = firstOfMonth.minusDays(1);
                        start = d.atStartOfDay();
                        end = d.plusDays(1).atStartOfDay();
                    }
                    default -> {
                        start = firstOfMonth.atStartOfDay();
                        end = firstOfMonth.plusMonths(1).atStartOfDay();
                    }
                }
            }
            default -> {
                LocalDate today = now.toLocalDate();
                switch (dateValue) {
                    case TODAY -> {
                        start = today.atStartOfDay();
                        end = today.plusDays(1).atStartOfDay();
                    }
                    case LAST_1_DAYS -> {
                        start = today.minusDays(1).atStartOfDay();
                        end = today.atStartOfDay();
                    }
                    case LAST_2_DAYS -> {
                        start = today.minusDays(2).atStartOfDay();
                        end = today.atStartOfDay();
                    }
                    case LAST_3_DAYS -> {
                        start = today.minusDays(3).atStartOfDay();
                        end = today.atStartOfDay();
                    }
                    case LAST_7_DAYS -> {
                        start = today.minusDays(7).atStartOfDay();
                        end = today.atStartOfDay();
                    }
                    default -> {
                        start = today.atStartOfDay();
                        end = today.plusDays(1).atStartOfDay();
                    }
                }
            }
        }
        return new LocalDateTime[]{start, end};
    }
}
