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

package io.github.zzih.rudder.publish.api.bundle;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单工作流发布载荷。项目归属、发起人、环境对象(数据源)放顶层,工作流本体放 {@link #workflow}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPublishBundle {

    private Long projectCode;

    private String projectName;

    private String projectDescription;

    /** 发起发布的用户名。 */
    private String userName;

    /**
     * 工作流引用到的数据源完整快照(已去重,含连接信息与明文凭证)。
     * 接收侧每次发布前据此 upsert 自身数据源注册表,再处理 workflow 任务定义。
     */
    private List<DatasourceBundle> datasources;

    /** 工作流引用到的资源(已去重,JAR / 配置文件 等),字节内联在 {@link ResourceBundle#getContent}。 */
    private List<ResourceBundle> resources;

    private WorkflowBundle workflow;
}
