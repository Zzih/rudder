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

package io.github.zzih.rudder.api.request;

import io.github.zzih.rudder.dao.enums.AuthSourceType;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新 auth source。patch 语义:字段为 null 表示不变。
 *
 * <p>{@link #type} 必须传(=记录现有 type),service 层忽略,不允许改 type。
 *
 * <p>更新 config 必须**完整重填**所有字段(包括 clientSecret / bindPassword 明文)。
 * v1 简化:不做"未变化字段保留",前端编辑时如不改敏感字段则提交时不带 config。
 */
@Data
public class AuthSourceUpdateRequest {

    private String name;

    @NotNull(message = "{validation.AuthSourceUpdateRequest.type.required}")
    private AuthSourceType type;

    /** 协议特定配置;为 null 表示本次不改 config。字段集合按 type 区分。 */
    private Map<String, Object> config;

    private Boolean enabled;

    private Integer priority;
}
