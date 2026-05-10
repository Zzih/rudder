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

package io.github.zzih.rudder.publish.api.bundle;

import io.github.zzih.rudder.common.param.Property;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流本体。不含项目归属与发起人信息(那些由 {@link WorkflowPublishBundle} /
 * {@link ProjectPublishBundle} 顶层承载,避免批量发布时重复)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowBundle {

    private Long code;

    private String name;

    private String description;

    /** DAG 原文 JSON,含节点 label / position 等视觉信息,由 provider 自行解析。 */
    private String dagJson;

    private List<TaskBundle> tasks;

    private List<EdgeBundle> edges;

    /** nullable,未配置调度时为空。 */
    private ScheduleBundle schedule;

    private List<Property> globalParams;
}
