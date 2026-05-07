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
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_script")
public class Script extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long code;

    private Long workspaceId;

    private Long dirId;

    private String name;

    private TaskType taskType;

    /**
     * 脚本内容（JSON: 与对应 TaskParams 结构一致）。
     */
    private String content;

    /**
     * 来源类型：IDE=IDE创建，TASK=工作流任务创建。
     */
    private SourceType sourceType;

    /**
     * 默认执行参数 JSON（Map&lt;String, String&gt;），IDE 保存时持久化。
     */
    private String params;
}
