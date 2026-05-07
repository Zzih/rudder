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

package io.github.zzih.rudder.service.auth.dto;

import io.github.zzih.rudder.dao.enums.AuthSourceType;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证源对外 DTO。字段 = AuthSource entity 的 admin 可见字段。
 * controllers / responses 通过 {@link AuthSourceDTO} 看到 AuthSource 数据,不再 import
 * {@code dao.entity.AuthSource}。configJson 已解密为明文。
 */
@Data
public class AuthSourceDTO {

    private Long id;
    private String name;
    private AuthSourceType type;
    private Boolean enabled;
    private Boolean isSystem;
    private Integer priority;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
