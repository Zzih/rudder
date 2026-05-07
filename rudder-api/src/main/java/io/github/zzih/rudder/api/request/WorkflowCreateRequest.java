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

package io.github.zzih.rudder.api.request;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowCreateRequest {

    @NotBlank
    private String name;

    private String description;

    private String dagJson;

    /**
     * 全局参数列表（Property 结构，direct=IN）。
     */
    private List<Property> globalParams;

    /**
     * DAG 节点对应的任务定义列表（与 dagJson 中的 nodes 一一对应）。
     */
    private List<TaskDefinitionDTO> taskDefinitions;

    // 调度配置（可选，随工作流一起保存）
    private String cronExpression;
    private String startTime;
    private String endTime;
    private String timezone;
}
