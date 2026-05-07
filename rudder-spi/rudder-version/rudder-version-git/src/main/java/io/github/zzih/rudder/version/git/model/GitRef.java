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

package io.github.zzih.rudder.version.git.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitVersionStore 自描述的 storageRef。序列化为 JSON 写到 {@code t_r_version_record.storage_ref} 列;
 * load 时反序列化拿回所有定位信息。
 *
 * <p>{@code multiFile} 标识工作流多文件场景:load 时需扫同目录下 tasks/ + scripts/ 重组完整 snapshotJson。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitRef {

    private String sha;

    private String path;

    private String org;

    private String repo;

    private boolean multiFile;
}
