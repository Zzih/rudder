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

import io.github.zzih.rudder.dao.enums.PublishStatus;
import io.github.zzih.rudder.dao.enums.PublishType;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

@Data
public class PublishBatchDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchCode;

    private PublishType publishType;
    private PublishStatus status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    private List<WorkflowItem> workflows;

    @Data
    public static class WorkflowItem {

        private String name;
        private Integer versionNo;
        private PublishStatus status;
    }
}
