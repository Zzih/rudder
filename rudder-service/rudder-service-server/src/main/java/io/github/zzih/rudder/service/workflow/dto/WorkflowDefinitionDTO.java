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

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Data;

/** WorkflowDefinition 对外 DTO,字段镜像 entity。 */
@Data
public class WorkflowDefinitionDTO {

    @JsonPropertyDescription("Internal id (server-assigned, ignored on create)")
    private Long id;

    @JsonPropertyDescription("Workflow stable code (server-generated)")
    private Long code;

    @JsonPropertyDescription("Owning workspace id (taken from auth context, ignored on create)")
    private Long workspaceId;

    @JsonPropertyDescription("Owning project code (must be set on create)")
    private Long projectCode;

    @JsonPropertyDescription("Workflow display name (project-unique)")
    private String name;

    @JsonPropertyDescription("Free-text description")
    private String description;

    @JsonPropertyDescription("DAG topology as JSON string. Schema: {\"nodes\": [{id, name, type, params, ...}], \"edges\": [{source, target, condition?}]}")
    private String dagJson;

    @JsonPropertyDescription("Workflow-level params, JSON array of {name, value, direct} entries (injected at run time)")
    private String globalParams;

    @JsonPropertyDescription("Currently published version id (set by workflow.publish, ignored on create/update)")
    private Long publishedVersionId;

    @JsonPropertyDescription("Creation timestamp (server-managed)")
    private LocalDateTime createdAt;

    @JsonPropertyDescription("Last update timestamp (server-managed)")
    private LocalDateTime updatedAt;
}
