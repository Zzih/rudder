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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.TaskDefinition;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

public final class TaskDefinitionConverter {

    private TaskDefinitionConverter() {
    }

    public static TaskDefinition toEntity(TaskDefinitionDTO dto) {
        TaskDefinition entity = new TaskDefinition();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setWorkflowDefinitionCode(dto.getWorkflowDefinitionCode());
        entity.setName(dto.getName());
        entity.setTaskType(dto.getTaskType());
        entity.setDescription(dto.getDescription());
        entity.setPriority(dto.getPriority());
        entity.setDelayTime(dto.getDelayTime());
        entity.setRetryTimes(dto.getRetryTimes());
        entity.setRetryInterval(dto.getRetryInterval());
        entity.setTimeout(dto.getTimeout());
        entity.setTimeoutEnabled(dto.getTimeoutEnabled());
        entity.setIsEnabled(dto.getIsEnabled());

        // timeoutNotifyStrategy: List<String> → JSON string
        if (dto.getTimeoutNotifyStrategy() != null) {
            entity.setTimeoutNotifyStrategy(JsonUtils.toJson(dto.getTimeoutNotifyStrategy()));
        }

        // scriptCode 从 script 对象获取
        if (dto.getScript() != null && dto.getScript().getCode() != null) {
            entity.setScriptCode(dto.getScript().getCode());
        }

        if (dto.getInputParams() != null) {
            entity.setInputParams(JsonUtils.toJson(dto.getInputParams()));
        }
        if (dto.getOutputParams() != null) {
            entity.setOutputParams(JsonUtils.toJson(dto.getOutputParams()));
        }

        return entity;
    }

    public static TaskDefinitionDTO toDTO(TaskDefinition entity) {
        TaskDefinitionDTO dto = new TaskDefinitionDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setWorkflowDefinitionCode(entity.getWorkflowDefinitionCode());
        dto.setName(entity.getName());
        dto.setTaskType(entity.getTaskType());
        dto.setDescription(entity.getDescription());
        dto.setPriority(entity.getPriority());
        dto.setDelayTime(entity.getDelayTime());
        dto.setRetryTimes(entity.getRetryTimes());
        dto.setRetryInterval(entity.getRetryInterval());
        dto.setTimeout(entity.getTimeout());
        dto.setTimeoutEnabled(entity.getTimeoutEnabled());
        dto.setIsEnabled(entity.getIsEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // timeoutNotifyStrategy: JSON string → List<String>
        dto.setTimeoutNotifyStrategy(parseStringList(entity.getTimeoutNotifyStrategy()));

        dto.setInputParams(parsePropertyList(entity.getInputParams()));
        dto.setOutputParams(parsePropertyList(entity.getOutputParams()));

        return dto;
    }

    private static List<Property> parsePropertyList(String json) {
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

    private static List<String> parseStringList(String json) {
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
