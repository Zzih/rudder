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

package io.github.zzih.rudder.datasource.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Beeline 风格 JDBC 引擎共享基类(Hive / Spark Thrift Server)。共性:
 * <ul>
 *   <li>URL 用分号分隔参数:{@code jdbc:hive2://h:p/db;auth=KERBEROS;principal=...}</li>
 *   <li>预览 SQL 不带反引号(老 server 不支持)</li>
 *   <li>fetchSize=1000(走父类 {@link #DEFAULT_FETCH_SIZE},无需 override)</li>
 *   <li>Kerberos 系统级配置(keytabPath/krb5ConfPath)不拼 URL,需 UGI loginUserFromKeytab —— 由
 *       {@link #nonUrlFields()} 声明排除</li>
 *   <li>{@code auth=NONE} 是 UI 占位值,Beeline 协议无此值,自动剔除</li>
 * </ul>
 * 子类只补自家 {@code dbType / propertiesClass / params / validate} + 可选 {@link #nonUrlFields()}。
 */
public abstract class AbstractBeelineJdbcTypeProvider<P> extends AbstractJdbcTypeProvider<P> {

    @Override
    protected String buildJdbcUrl(String host, int port, String database, P props) {
        // nonNullFields 在 props=null 时返回 Map.of() 不可变,这里 LinkedHashMap copy 让 remove 可用。
        Map<String, String> kv = new LinkedHashMap<>(nonNullFields(props));
        kv.keySet().removeAll(nonUrlFields());
        if ("NONE".equals(kv.get("auth"))) {
            kv.remove("auth");
        }
        return appendSemicolon(dbType().buildJdbcUrl(host, port, database), kv);
    }

    /** record 字段中不应拼到 URL 的(Kerberos 系统级配置等)。默认空。 */
    protected Set<String> nonUrlFields() {
        return Set.of();
    }

    @Override
    public String buildPreviewSql(String database, String table, int limit) {
        return "SELECT * FROM " + database + "." + table + " LIMIT " + limit;
    }
}
