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

package io.github.zzih.rudder.api.response;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowScheduleDTO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

@Data
public class WorkflowResponse {

    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long code;
    private Long workspaceId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectCode;
    private String name;
    private String description;
    private String dagJson;
    private List<Property> globalParams;
    private Long publishedVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<TaskDefinitionDTO> taskDefinitions;

    // 调度信息（如果没有调度配置则为 null）
    private String cronExpression;
    private String startTime;
    private String endTime;
    private String timezone;
    private String scheduleStatus;

    /** 内容指纹(SHA-256 hex);保存时回传,后端比对当前 DB 现状的 hash 实现乐观锁。 */
    private String contentHash;

    public static WorkflowResponse from(WorkflowDefinitionDTO wf, WorkflowScheduleDTO schedule) {
        return from(wf, schedule, null, null);
    }

    public static WorkflowResponse from(WorkflowDefinitionDTO wf, WorkflowScheduleDTO schedule,
                                        List<TaskDefinitionDTO> taskDefs, String contentHash) {
        WorkflowResponse vo = new WorkflowResponse();
        vo.setId(wf.getId());
        vo.setCode(wf.getCode());
        vo.setWorkspaceId(wf.getWorkspaceId());
        vo.setProjectCode(wf.getProjectCode());
        vo.setName(wf.getName());
        vo.setDescription(wf.getDescription());
        vo.setDagJson(normalizeDagJson(wf.getDagJson()));
        vo.setGlobalParams(parseGlobalParams(wf.getGlobalParams()));
        vo.setPublishedVersionId(wf.getPublishedVersionId());
        vo.setCreatedAt(wf.getCreatedAt());
        vo.setUpdatedAt(wf.getUpdatedAt());
        vo.setTaskDefinitions(taskDefs);
        vo.setContentHash(contentHash);
        if (schedule != null) {
            vo.setCronExpression(schedule.getCronExpression());
            vo.setStartTime(schedule.getStartTime() != null ? schedule.getStartTime().toString() : null);
            vo.setEndTime(schedule.getEndTime() != null ? schedule.getEndTime().toString() : null);
            vo.setTimezone(schedule.getTimezone());
            vo.setScheduleStatus(schedule.getStatus() != null ? schedule.getStatus().name() : null);
        }
        return vo;
    }

    /**
     * 规范化 dagJson：parse 再 serialize，确保 taskCode 等 Long 字段以字符串形式输出，
     * 避免前端 JS Number 精度丢失。
     */
    private static String normalizeDagJson(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) {
            return dagJson;
        }
        try {
            var graph = JsonUtils.fromJson(dagJson, DagGraph.class);
            return JsonUtils.toJson(graph);
        } catch (Exception e) {
            return dagJson;
        }
    }

    private static List<Property> parseGlobalParams(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return JsonUtils.fromJson(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
