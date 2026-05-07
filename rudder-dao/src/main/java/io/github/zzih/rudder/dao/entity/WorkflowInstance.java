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
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.TriggerType;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_workflow_instance")
public class WorkflowInstance extends BaseEntity {

    private Long workspaceId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowDefinitionCode;

    /**
     * 实例名称，例如 "daily_etl_20260327_143052_001"
     */
    private String name;

    private Long versionId;

    /**
     * 触发类型：MANUAL 或 SCHEDULER。
     */
    private TriggerType triggerType;

    private InstanceStatus status;

    /**
     * 实例创建时的 DAG 快照（LONGTEXT）。
     */
    private String dagSnapshot;

    /**
     * JSON 格式的运行时参数（TEXT）。
     */
    private String runtimeParams;

    /**
     * JSON 格式的变量池（TEXT）。
     */
    private String varPool;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /**
     * 持有该工作流 runner 的 Server RPC 地址（host:port）。
     * <p>
     * 用于多 Server 部署下的孤儿识别：若 owner_host 不在
     * {@code t_r_service_registry} 的在线列表里，则该 wf 被视为孤儿，
     * 由 {@code WorkflowOrphanService} 定时回收。
     */
    private String ownerHost;
}
