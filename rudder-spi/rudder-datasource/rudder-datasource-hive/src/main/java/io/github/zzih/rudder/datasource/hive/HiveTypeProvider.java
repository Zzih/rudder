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

package io.github.zzih.rudder.datasource.hive;

import io.github.zzih.rudder.datasource.api.AbstractBeelineJdbcTypeProvider;
import io.github.zzih.rudder.datasource.api.DatasourceTypeProvider;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.util.Set;

import com.google.auto.service.AutoService;

@AutoService(DatasourceTypeProvider.class)
public class HiveTypeProvider extends AbstractBeelineJdbcTypeProvider<HiveConnectionProperties> {

    private static final Set<String> VALID_AUTH = Set.of("NONE", "KERBEROS", "LDAP");
    private static final Set<String> VALID_TRANSPORT = Set.of("binary", "http");

    @Override
    public DatasourceType dbType() {
        return DatasourceType.HIVE;
    }

    @Override
    public Class<HiveConnectionProperties> propertiesClass() {
        return HiveConnectionProperties.class;
    }

    @Override
    protected String defaultParamsJson() {
        return """
                {
                  "auth": "NONE",
                  "transportMode": "binary",
                  "principal": "hive/_HOST@REALM.COM",
                  "keytabPath": "/etc/security/keytabs/hive.keytab",
                  "krb5ConfPath": "/etc/krb5.conf",
                  "httpPath": "cliservice"
                }""";
    }

    @Override
    public ValidationResult validate(HiveConnectionProperties props) {
        if (props == null) {
            return ValidationResult.ok();
        }
        if (props.auth() != null && !VALID_AUTH.contains(props.auth())) {
            return ValidationResult.fail("auth",
                    "auth must be one of " + VALID_AUTH + ", got: " + props.auth());
        }
        if ("KERBEROS".equals(props.auth())) {
            if (isBlank(props.principal())) {
                return ValidationResult.fail("principal", "principal is required when auth=KERBEROS");
            }
            if (isBlank(props.keytabPath())) {
                return ValidationResult.fail("keytabPath", "keytabPath is required when auth=KERBEROS");
            }
        }
        if (props.transportMode() != null && !VALID_TRANSPORT.contains(props.transportMode())) {
            return ValidationResult.fail("transportMode",
                    "transportMode must be one of " + VALID_TRANSPORT + ", got: " + props.transportMode());
        }
        if ("http".equals(props.transportMode()) && isBlank(props.httpPath())) {
            return ValidationResult.fail("httpPath", "httpPath is required when transportMode=http");
        }
        return ValidationResult.ok();
    }

    /** Kerberos 系统级配置(需 UGI loginUserFromKeytab),不能拼 URL。 */
    @Override
    protected Set<String> nonUrlFields() {
        return Set.of("keytabPath", "krb5ConfPath");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
