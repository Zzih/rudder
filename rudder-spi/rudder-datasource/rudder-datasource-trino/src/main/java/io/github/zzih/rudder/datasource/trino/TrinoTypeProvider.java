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

package io.github.zzih.rudder.datasource.trino;

import io.github.zzih.rudder.datasource.api.AbstractJdbcTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.sql.SQLException;
import java.sql.Statement;

import com.google.auto.service.AutoService;

/** Trino provider。三层(catalog.database.table),预览 SQL 不带反引号、fetchSize=1000。 */
@AutoService(DatasourceTypeProvider.class)
public class TrinoTypeProvider extends AbstractJdbcTypeProvider<TrinoConnectionProperties> {

    private static final int DEFAULT_FETCH_SIZE = 1000;

    @Override
    public DatasourceType dbType() {
        return DatasourceType.TRINO;
    }

    @Override
    public Class<TrinoConnectionProperties> propertiesClass() {
        return TrinoConnectionProperties.class;
    }

    @Override
    protected String defaultParamsJson() {
        return """
                {
                  "SSL": false,
                  "SSLTrustStorePath": "",
                  "SSLTrustStorePassword": "",
                  "accessToken": "",
                  "httpProxy": ""
                }""";
    }

    @Override
    public void applyStreamingFetch(Statement stmt) throws SQLException {
        stmt.setFetchSize(DEFAULT_FETCH_SIZE);
    }

    @Override
    public String buildPreviewSql(String database, String table, int limit) {
        return "SELECT * FROM " + database + "." + table + " LIMIT " + limit;
    }

    @Override
    public ValidationResult validate(TrinoConnectionProperties props) {
        if (props == null) {
            return ValidationResult.ok();
        }
        // SSL=true 且提供 trust store 必须同时给密码,避免 driver 报隐晦错误
        if (Boolean.TRUE.equals(props.SSL())
                && props.SSLTrustStorePath() != null && !props.SSLTrustStorePath().isBlank()
                && (props.SSLTrustStorePassword() == null || props.SSLTrustStorePassword().isBlank())) {
            return ValidationResult.fail("SSLTrustStorePassword",
                    "SSLTrustStorePassword is required when SSLTrustStorePath is provided");
        }
        return ValidationResult.ok();
    }
}
