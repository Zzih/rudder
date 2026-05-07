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

package io.github.zzih.rudder.service.workflow.controlflow.condition;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.dao.dao.TaskInstanceDao;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.workflow.controlflow.AbstractControlFlowTask;
import io.github.zzih.rudder.service.workflow.controlflow.BranchDecision;
import io.github.zzih.rudder.service.workflow.controlflow.DependRelation;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * CONDITION 控制流任务。
 * <p>
 * 检查前置任务的执行状态是否满足条件（AND / OR 组合），
 * 根据结果决定走成功分支还是失败分支。
 */
@Slf4j
public class ConditionTask extends AbstractControlFlowTask {

    private final Long workflowInstanceId;
    private final String configJson;
    private final TaskInstanceDao taskInstanceDao;

    public ConditionTask(Long workflowInstanceId, String configJson, TaskInstanceDao taskInstanceDao) {
        this.workflowInstanceId = workflowInstanceId;
        this.configJson = configJson;
        this.taskInstanceDao = taskInstanceDao;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle() throws TaskException {
        try {
            Map<String, Object> config = parseConfig(configJson);

            Map<String, Object> dependence = (Map<String, Object>) config.getOrDefault("dependence", Map.of());
            List<Map<String, Object>> dependTaskList =
                    (List<Map<String, Object>>) dependence.getOrDefault("dependTaskList", List.of());
            DependRelation outerRelation =
                    DependRelation.valueOf(dependence.getOrDefault("relation", "AND").toString());

            log.info("[Condition] Start evaluating {} dependency group(s), outerRelation={}", dependTaskList.size(),
                    outerRelation);

            // 一次取同 wf 实例下所有 taskCode 的最新 instance，本地 map 查询替代循环里的 N+1
            Map<Long, TaskInstance> latestByCode =
                    taskInstanceDao.selectLatestByWorkflowInstanceIdGroupedByTaskCode(workflowInstanceId);

            List<Boolean> groupResults = new ArrayList<>();
            for (int gi = 0; gi < dependTaskList.size(); gi++) {
                Map<String, Object> group = dependTaskList.get(gi);
                List<Map<String, Object>> items =
                        (List<Map<String, Object>>) group.getOrDefault("dependItemList", List.of());
                DependRelation innerRelation = DependRelation.valueOf(group.getOrDefault("relation", "AND").toString());

                List<Boolean> itemResults = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Long depTaskCode = item.get("depTaskCode") != null
                            ? Long.valueOf(item.get("depTaskCode").toString())
                            : null;
                    InstanceStatus expectedStatus =
                            InstanceStatus.valueOf(item.getOrDefault("status", "SUCCESS").toString());
                    if (depTaskCode == null) {
                        log.warn("[Condition]   Item skipped: depTaskCode is null");
                        itemResults.add(false);
                        continue;
                    }
                    TaskInstance depTask = latestByCode.get(depTaskCode);
                    boolean ok = depTask != null && depTask.getStatus() == expectedStatus;
                    log.info("[Condition]   Task (code={}) expected={}, actual={}, satisfied={}",
                            depTaskCode, expectedStatus,
                            depTask != null ? depTask.getStatus() : "not found", ok);
                    itemResults.add(ok);
                }

                boolean groupResult = innerRelation == DependRelation.AND
                        ? itemResults.stream().allMatch(Boolean::booleanValue)
                        : itemResults.stream().anyMatch(Boolean::booleanValue);
                log.info("[Condition] Group {} result: items={}, groupSatisfied={}", gi, itemResults, groupResult);
                groupResults.add(groupResult);
            }

            boolean conditionSuccess = groupResults.isEmpty() || (outerRelation == DependRelation.AND
                    ? groupResults.stream().allMatch(Boolean::booleanValue)
                    : groupResults.stream().anyMatch(Boolean::booleanValue));

            Map<String, Object> conditionResult =
                    (Map<String, Object>) config.getOrDefault("conditionResult", Map.of());
            List<?> successNodes = (List<?>) conditionResult.getOrDefault("successNode", List.of());
            List<?> failedNodes = (List<?>) conditionResult.getOrDefault("failedNode", List.of());

            Set<Long> allowed = new HashSet<>();
            Set<Long> skipped = new HashSet<>();
            if (conditionSuccess) {
                successNodes.forEach(n -> allowed.add(Long.valueOf(n.toString())));
                failedNodes.forEach(n -> skipped.add(Long.valueOf(n.toString())));
            } else {
                failedNodes.forEach(n -> allowed.add(Long.valueOf(n.toString())));
                successNodes.forEach(n -> skipped.add(Long.valueOf(n.toString())));
            }

            log.info("Condition evaluated: success={} allowed={} skipped={}", conditionSuccess, allowed, skipped);
            setBranchDecision(new BranchDecision(allowed, skipped));
            status = TaskStatus.SUCCESS;

        } catch (Exception e) {
            status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                    "Condition evaluation failed: " + e.getMessage());
        }
    }
}
