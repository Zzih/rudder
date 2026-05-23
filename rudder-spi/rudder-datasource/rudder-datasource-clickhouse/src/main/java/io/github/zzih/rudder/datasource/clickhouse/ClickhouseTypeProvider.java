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

package io.github.zzih.rudder.datasource.clickhouse;

import io.github.zzih.rudder.datasource.api.AbstractJdbcTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.SQLException;
import java.sql.Statement;

import com.google.auto.service.AutoService;

/** ClickHouse provider。预览 SQL 带反引号(基类默认),fetchSize=1000。 */
@AutoService(DatasourceTypeProvider.class)
public class ClickhouseTypeProvider extends AbstractJdbcTypeProvider<ClickhouseConnectionProperties> {

    private static final int DEFAULT_FETCH_SIZE = 1000;

    @Override
    public DatasourceType dbType() {
        return DatasourceType.CLICKHOUSE;
    }

    @Override
    public Class<ClickhouseConnectionProperties> propertiesClass() {
        return ClickhouseConnectionProperties.class;
    }

    @Override
    protected String defaultParamsJson() {
        return """
                {
                  "socket_timeout": 30000,
                  "compress": true,
                  "ssl": false
                }""";
    }

    @Override
    public void applyStreamingFetch(Statement stmt) throws SQLException {
        stmt.setFetchSize(DEFAULT_FETCH_SIZE);
    }
}
