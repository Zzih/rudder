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

package io.github.zzih.rudder.service.workflow.controlflow.subworkflow;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.service.workflow.controlflow.AbstractControlFlowTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * SUB_WORKFLOW 控制流任务。
 * <p>
 * 通过 {@link SubWorkflowExecutor} 接口创建子工作流实例并同步等待其完成。
 * 子工作流的 OUT 变量通过 {@link #getVarPool()} 返回,由 Runner 合并到父工作流变量池。
 * 子→父冒泡按子工作流定义级 globalParams 中 {@code Direct.OUT} 过滤(在 SubWorkflowExecutor
 * 实现侧完成),控制流 task 这层只透传。
 */
@Slf4j
public class SubWorkflowTask extends AbstractControlFlowTask {

    private final String configJson;
    private final Set<Long> ancestorWorkflowDefinitionCodes;
    private final List<Property> varPool;
    private final SubWorkflowExecutor subWorkflowExecutor;

    private List<Property> outputVarPool;

    public SubWorkflowTask(String configJson, Set<Long> ancestorWorkflowDefinitionCodes,
                           List<Property> varPool, SubWorkflowExecutor subWorkflowExecutor) {
        this.configJson = configJson;
        this.ancestorWorkflowDefinitionCodes = ancestorWorkflowDefinitionCodes;
        this.varPool = varPool;
        this.subWorkflowExecutor = subWorkflowExecutor;
    }

    @Override
    public void handle() throws TaskException {
        try {
            Map<String, Object> config = parseConfig(configJson);
            Object codeObj = config.get("workflowDefinitionCode");
            if (codeObj == null) {
                throw new TaskException(TaskErrorCode.TASK_SUB_WORKFLOW_MISSING_CODE);
            }

            Long subWorkflowDefinitionCode = Long.valueOf(codeObj.toString());
            log.info("[SubWorkflow] Launching sub-workflow code={}, ancestors={}", subWorkflowDefinitionCode,
                    ancestorWorkflowDefinitionCodes);

            if (ancestorWorkflowDefinitionCodes.contains(subWorkflowDefinitionCode)) {
                log.error("[SubWorkflow] Circular dependency detected: code={} already in ancestors={}",
                        subWorkflowDefinitionCode, ancestorWorkflowDefinitionCodes);
                throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                        "Circular sub-workflow dependency: " + subWorkflowDefinitionCode);
            }

            outputVarPool = subWorkflowExecutor.executeSubWorkflow(
                    subWorkflowDefinitionCode, ancestorWorkflowDefinitionCodes, varPool);

            log.info("[SubWorkflow] Sub-workflow code={} completed, outputs={}", subWorkflowDefinitionCode,
                    outputVarPool != null ? outputVarPool.stream().map(Property::getProp).toList() : "none");
            status = TaskStatus.SUCCESS;

        } catch (TaskException e) {
            status = TaskStatus.FAILED;
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = TaskStatus.CANCELLED;
            throw new TaskException(TaskErrorCode.TASK_SUB_WORKFLOW_INTERRUPTED);
        } catch (Exception e) {
            status = TaskStatus.FAILED;
            throw new TaskException(TaskErrorCode.TASK_SUB_WORKFLOW_FAILED, e.getMessage());
        }
    }

    @Override
    public List<Property> getVarPool() {
        return outputVarPool != null ? outputVarPool : Collections.emptyList();
    }
}
