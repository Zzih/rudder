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

import io.github.zzih.rudder.dao.enums.ScheduleStatus;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Data;

/** WorkflowSchedule 对外 DTO,字段镜像 entity。 */
@Data
public class WorkflowScheduleDTO {

    @JsonPropertyDescription("Internal id (server-managed)")
    private Long id;

    @JsonPropertyDescription("Bound workflow definition code (taken from path, ignored in body)")
    private Long workflowDefinitionCode;

    @JsonPropertyDescription("Cron expression in Quartz syntax, e.g. '0 0 2 * * ?' (daily at 2 AM)")
    private String cronExpression;

    @JsonPropertyDescription("Schedule effective start (ISO-8601 local datetime, optional; null = effective immediately)")
    private LocalDateTime startTime;

    @JsonPropertyDescription("Schedule effective end (ISO-8601 local datetime, optional; null = no expiry)")
    private LocalDateTime endTime;

    @JsonPropertyDescription("IANA timezone for cron evaluation, e.g. 'Asia/Shanghai' (default 'UTC')")
    private String timezone;

    @JsonPropertyDescription("ONLINE = enabled / OFFLINE = paused (default ONLINE)")
    private ScheduleStatus status;

    @JsonPropertyDescription("Creation timestamp (server-managed)")
    private LocalDateTime createdAt;

    @JsonPropertyDescription("Last update timestamp (server-managed)")
    private LocalDateTime updatedAt;
}
