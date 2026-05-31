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

package io.github.zzih.rudder.dao.entity.view;

import io.github.zzih.rudder.dao.entity.DataPermBundleStatementResource;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库表行扁平视图:resource + 所属 block(bundleId/scopeCode/groupIds)+ JOIN scope 取展示/物化元数据。
 * 一条 resource 一行,reconciler 与权限包展示共用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataPermBundleStatementResourceView extends DataPermBundleStatementResource {

    private Long bundleId;
    private Long scopeCode;
    /** 所属 block 的操作分组 id 列表(JSON 字符串)。 */
    private String groupIds;

    private String scopeName;
    private String pluginType;
    private String rangerServiceName;
    private Boolean scopeEnabled;
}
