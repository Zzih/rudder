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

import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.dto.AuthSourceDTO;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Admin 列表条目。不含 configJson,前端列表渲染只需要这些字段;详情走 detail。
 */
@Data
public class AuthSourceSummaryResponse {

    private Long id;
    private String name;
    private AuthSourceType type;
    private Boolean enabled;
    private Boolean isSystem;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AuthSourceSummaryResponse from(AuthSourceDTO source) {
        AuthSourceSummaryResponse r = new AuthSourceSummaryResponse();
        r.setId(source.getId());
        r.setName(source.getName());
        r.setType(source.getType());
        r.setEnabled(source.getEnabled());
        r.setIsSystem(source.getIsSystem());
        r.setPriority(source.getPriority());
        r.setCreatedAt(source.getCreatedAt());
        r.setUpdatedAt(source.getUpdatedAt());
        return r;
    }
}
