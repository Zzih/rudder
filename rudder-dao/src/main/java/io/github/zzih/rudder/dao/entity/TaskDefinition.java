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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.entity.BaseEntity;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_task_definition")
public class TaskDefinition extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long code;

    private Long workspaceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowDefinitionCode;

    private String name;

    private TaskType taskType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long scriptCode;

    private String description;

    /**
     * JSON: 输入参数映射。
     */
    private String inputParams;

    /**
     * JSON: 输出参数映射。
     */
    private String outputParams;

    private String priority;

    private Integer delayTime;

    private Integer retryTimes;

    private Integer retryInterval;

    private Integer timeout;

    @TableField("timeout_enabled")
    private Boolean timeoutEnabled;

    private String timeoutNotifyStrategy;

    @TableField("is_enabled")
    private Boolean isEnabled = true;
}
