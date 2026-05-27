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

package io.github.zzih.rudder.spi.api.context;

import java.util.Map;
import java.util.Properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 跨 SPI 共享的数据源描述,provider 按需取字段。所有字段在 DatasourceService 一次性填齐。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceInfo {

    private String name;
    private String type;
    /** 三层引擎(StarRocks/Trino) = true,两层引擎(MySQL/Hive) = false。 */
    private boolean hasCatalog;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClass;
    private Map<String, String> properties;
    /** Provider 自报的探活 SQL,HikariCP {@code connectionTestQuery} 用它。默认 {@code SELECT 1}。 */
    private String validationQuery;

    /**
     * 连接用 Properties:account/password 以 JDBC 标准 {@code user}/{@code password} key 并入
     * {@link #properties}。所有连接路径(池化 HikariCP / task / test / preview / metadata)共用,
     * 保证用户配置的参数在每条路径一致生效。
     */
    public Properties toConnectionProperties() {
        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        if (properties != null) {
            // Properties 不接受 null value;用户配置里的 null 项直接跳过。
            properties.forEach((k, v) -> {
                if (v != null) {
                    props.setProperty(k, v);
                }
            });
        }
        return props;
    }
}
