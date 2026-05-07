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

package io.github.zzih.rudder.version.api.model;

import io.github.zzih.rudder.common.enums.datatype.ResourceType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * SPI 视角的版本记录。
 * <p>
 * {@code save} 路径上 {@link #content} 持有真实内容,各 provider 自行决定如何存到后端;
 * {@code load} 路径上由调用方填充 {@code provider 自描述 storageRef 解析所需的字段(如 GIT 的 commit SHA、path、org、repo)} 通过 {@link #attributes} 传递。
 */
@Data
public class VersionRecord {

    private Long id;

    private ResourceType resourceType;

    private Long resourceCode;

    private Integer versionNo;

    /** 真实内容(脚本文本 / 工作流 snapshotJson)。在 save 时由调用方填入,在 load 返回时由 provider 填入。 */
    private String content;

    private String remark;

    private Long createdBy;

    private LocalDateTime createdAt;

    /**
     * 调用方传给 SPI 的自由 metadata。
     * LOCAL 实现忽略;GIT 实现读取 {@code orgName / repoName / filePath} 等键来定位 Gitea 路径。
     */
    private Map<String, String> attributes = new LinkedHashMap<>();
}
