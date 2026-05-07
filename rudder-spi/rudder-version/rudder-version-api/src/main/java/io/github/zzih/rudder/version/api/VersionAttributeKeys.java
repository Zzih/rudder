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

package io.github.zzih.rudder.version.api;

import io.github.zzih.rudder.version.api.model.VersionRecord;

/**
 * {@link VersionRecord#getAttributes()} 中使用的标准 key 常量。
 * 调用方写入,各 provider 实现读取。LOCAL provider 不读任何 attribute,GIT provider 用以下三个定位文件位置。
 */
public final class VersionAttributeKeys {

    private VersionAttributeKeys() {
    }

    /** Git 组织名(对应工作空间名)。 */
    public static final String ORG_NAME = "orgName";

    /** Git 仓库名(工作流=项目名,脚本固定 "ide")。 */
    public static final String REPO_NAME = "repoName";

    /** 仓库内文件路径(如 {@code workflows/etl_daily/dag.json})。 */
    public static final String FILE_PATH = "filePath";
}
