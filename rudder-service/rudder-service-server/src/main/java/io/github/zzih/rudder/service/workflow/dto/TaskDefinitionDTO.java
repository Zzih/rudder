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

package io.github.zzih.rudder.service.workflow.dto;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

@Data
public class TaskDefinitionDTO {

    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long code;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowDefinitionCode;

    private String name;

    private TaskType taskType;

    /**
     * 关联的脚本对象。所有任务类型都通过 Script 存储配置：
     * - SQL/SCRIPT: content 存脚本内容
     * - JAR: content 存 JAR 配置 JSON
     * - 控制流: content 存控制流参数 JSON
     */
    private Script script;

    private String description;

    private List<Property> inputParams;

    private List<Property> outputParams;

    private String priority;

    private Integer delayTime;

    private Integer retryTimes;

    private Integer retryInterval;

    private Integer timeout;

    private Boolean timeoutEnabled;

    private List<String> timeoutNotifyStrategy;

    private Boolean isEnabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
