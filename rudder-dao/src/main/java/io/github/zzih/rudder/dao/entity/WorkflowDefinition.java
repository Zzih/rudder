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

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_workflow_definition")
public class WorkflowDefinition extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long code;

    private Long workspaceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectCode;

    private String name;

    private String description;

    /**
     * JSON 格式的 DAG 定义（LONGTEXT）。
     */
    private String dagJson;

    /**
     * JSON 格式的全局参数（TEXT）。
     */
    private String globalParams;

    private Long publishedVersionId;
}
