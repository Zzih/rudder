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

package io.github.zzih.rudder.datasource.doris;

import io.github.zzih.rudder.datasource.api.AbstractJdbcTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.SQLException;
import java.sql.Statement;

import com.google.auto.service.AutoService;

/** Doris 走 MySQL JDBC 驱动,流式策略同 MySQL,但默认 useSSL=false + allowPublicKeyRetrieval=true。 */
@AutoService(DatasourceTypeProvider.class)
public class DorisTypeProvider extends AbstractJdbcTypeProvider<DorisConnectionProperties> {

    @Override
    public DatasourceType dbType() {
        return DatasourceType.DORIS;
    }

    @Override
    public Class<DorisConnectionProperties> propertiesClass() {
        return DorisConnectionProperties.class;
    }

    @Override
    protected String defaultParamsJson() {
        return """
                {
                  "useSSL": false,
                  "characterEncoding": "UTF-8",
                  "allowPublicKeyRetrieval": true,
                  "connectTimeout": 10000
                }""";
    }

    @Override
    public void applyStreamingFetch(Statement stmt) throws SQLException {
        stmt.setFetchSize(Integer.MIN_VALUE);
    }
}
