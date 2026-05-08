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

import io.github.zzih.rudder.task.api.task.enums.TaskType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个任务在发布时的快照。scriptContent / configJson 均为原始 JSON 字符串，由 provider 自行解析。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskBundle {

    private Long taskCode;

    private String name;

    private String description;

    private TaskType taskType;

    /** 普通任务体（JSON）。控制流任务为 null。 */
    private String scriptContent;

    /** 控制流任务专用配置（JSON）。普通任务为 null。 */
    private String configJson;

    private Integer retryTimes;

    private Integer retryInterval;

    private Integer timeout;
}
