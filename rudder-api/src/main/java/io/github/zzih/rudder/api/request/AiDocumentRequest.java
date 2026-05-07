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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新 AI 文档的请求体。不携带 BaseEntity 审计字段、indexedAt 等服务端管理字段。
 *
 * <p>{@code workspaceIds}: JSON 数组字符串(如 {@code "[1,3]"});null 表示平台共享。
 */
@Data
public class AiDocumentRequest {

    private String workspaceIds;

    @NotBlank
    private String docType;

    private String engineType;

    private String sourceRef;

    @NotBlank
    private String title;

    private String content;

    private String description;
}
