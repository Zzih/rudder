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

package io.github.zzih.rudder.service.workspace.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

@Data
public class ProjectDTO {

    @JsonPropertyDescription("Internal id (server-assigned, ignored on create)")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @JsonPropertyDescription("Project stable code (server-generated, used as path key in REST URLs)")
    private Long code;

    @JsonPropertyDescription("Owning workspace id (taken from auth context, ignored on create)")
    private Long workspaceId;

    @JsonPropertyDescription("Project display name (workspace-unique)")
    private String name;

    @JsonPropertyDescription("Free-text description")
    private String description;

    @JsonPropertyDescription("Project-level runtime params, serialized as JSON array of {name, value, direct} entries")
    private String params;

    @JsonPropertyDescription("Owner user id (server-managed; use project.update_owner to change)")
    private Long createdBy;

    @JsonPropertyDescription("Owner username (server-resolved)")
    private String createdByUsername;

    @JsonPropertyDescription("Creation timestamp (server-managed)")
    private LocalDateTime createdAt;

    @JsonPropertyDescription("Last update timestamp (server-managed)")
    private LocalDateTime updatedAt;
}
