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

import io.github.zzih.rudder.service.workflow.controlflow.DependRelation;
import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DependentTaskParams extends AbstractTaskParams {

    private Dependence dependence;

    @Override
    public boolean validate() {
        return dependence != null
                && dependence.getDependTaskList() != null
                && !dependence.getDependTaskList().isEmpty();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.DEPENDENT;
    }

    @Data
    public static class Dependence {

        private List<DependTaskGroup> dependTaskList;
        private DependRelation relation = DependRelation.AND;
        private Integer checkInterval = 10;
        private FailurePolicy failurePolicy = FailurePolicy.DEPENDENT_FAILURE_FAILURE;
        private Integer failureWaitingTime = 1;
    }

    @Data
    public static class DependTaskGroup {

        private List<DependItem> dependItemList;
        private DependRelation relation = DependRelation.AND;
    }

    @Data
    public static class DependItem {

        private String dependentType = "DEPENDENT";
        private Long projectCode;
        private Long definitionCode;
        private Long depTaskCode;
        private DependCycle cycle;
        private DateValue dateValue;
        private Boolean parameterPassing = false;
    }
}
