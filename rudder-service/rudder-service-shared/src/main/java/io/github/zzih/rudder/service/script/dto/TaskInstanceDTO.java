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

package io.github.zzih.rudder.service.script.dto;

import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

@Data
public class TaskInstanceDTO {

    private Long id;
    private String name;
    private Long taskDefinitionCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long scriptCode;

    private TaskType taskType;
    private String content;
    private String params;
    private SourceType sourceType;
    private Long workflowInstanceId;
    private InstanceStatus status;
    private String appId;
    private String trackingUrl;
    private String executionHost;
    private String logPath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long duration;
    private String errorMessage;
    private String resultPath;
    private Long rowCount;
    private String varPool;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
