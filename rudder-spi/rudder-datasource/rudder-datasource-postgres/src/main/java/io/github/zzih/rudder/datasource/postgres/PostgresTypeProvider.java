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

package io.github.zzih.rudder.datasource.postgres;

import io.github.zzih.rudder.datasource.api.AbstractJdbcTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import com.google.auto.service.AutoService;

/** Postgres provider。游标流式要求 {@code autoCommit=false},否则 fetchSize 不生效。 */
@AutoService(DatasourceTypeProvider.class)
public class PostgresTypeProvider extends AbstractJdbcTypeProvider<PostgresConnectionProperties> {

    private static final int CURSOR_FETCH_SIZE = 1000;

    @Override
    public DatasourceType dbType() {
        return DatasourceType.POSTGRES;
    }

    @Override
    public Class<PostgresConnectionProperties> propertiesClass() {
        return PostgresConnectionProperties.class;
    }

    @Override
    protected String defaultParamsJson() {
        return """
                {
                  "sslmode": "disable",
                  "applicationName": "rudder",
                  "connectTimeout": 10,
                  "sslcert": "",
                  "sslkey": "",
                  "sslrootcert": ""
                }""";
    }

    @Override
    public void applyStreamingFetch(Statement stmt) throws SQLException {
        stmt.getConnection().setAutoCommit(false);
        stmt.setFetchSize(CURSOR_FETCH_SIZE);
    }

    private static final Set<String> VALID_SSL_MODES =
            Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

    @Override
    public ValidationResult validate(PostgresConnectionProperties props) {
        if (props == null) {
            return ValidationResult.ok();
        }
        if (props.sslmode() != null && !VALID_SSL_MODES.contains(props.sslmode())) {
            return ValidationResult.fail("sslmode",
                    "sslmode must be one of " + VALID_SSL_MODES + ", got: " + props.sslmode());
        }
        if (props.connectTimeout() != null && props.connectTimeout() < 0) {
            return ValidationResult.fail("connectTimeout", "connectTimeout must be >= 0");
        }
        return ValidationResult.ok();
    }
}
