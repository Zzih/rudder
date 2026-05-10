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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源完整快照。随每次工作流发布 piggyback 在 {@code WorkflowBundle.datasources} 中,
 * 接收侧据此 upsert 自身数据源注册表,实现"发布即环境就绪"。
 *
 * <p>{@code credential} 为明文 JSON,依赖 HTTPS 传输保护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasourceBundle {

    /** 与 {@code TaskBundle.scriptContent.dataSourceId} 对齐的 join key。 */
    private Long id;

    private String name;

    /** STARROCKS / MYSQL / HIVE / TRINO / ... */
    private String type;

    private String host;

    private Integer port;

    /** JDBC URL host/port 后那一段:MySQL/PG → database,Hive → default schema,Trino → catalog。 */
    private String defaultPath;

    /** 额外连接参数 JSON。 */
    private String params;

    /** 凭证 JSON 明文(如 {"username":"x","password":"y"})。 */
    private String credential;
}
