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

import io.github.zzih.rudder.common.sql.SqlDialect;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 默认 JDBC Provider 实现。子类只覆盖与默认行为不一致的部分。
 *
 * <p>子类必须自行实现 SPI 标准方法 {@code dbType / propertiesClass / params}。
 *
 * <p>典型场景下子类只需 override {@link #buildJdbcUrl(String, int, String, Object)}(强类型重载)
 * 来把自家 properties 拼到 URL 上;基类负责 JSON→typed 桥接。
 *
 * @param <P> 该 DB 的强类型 properties POJO 类型
 */
public abstract class AbstractJdbcTypeProvider<P> implements DatasourceTypeProvider<P> {

    /** 默认连接探活超时,秒。 */
    protected static final int VALIDATE_TIMEOUT_SECONDS = 5;

    /** 默认流式 fetchSize 兜底。子类未覆盖时按此设置,与原 {@code SqlExecutor} 行为对齐。 */
    protected static final int DEFAULT_FETCH_SIZE = 1000;

    @Override
    public String getDriverClass() {
        return dbType().getDriverClassName();
    }

    /**
     * 基类把 JSON 反序列化为强类型 P,再转调 {@link #buildJdbcUrl(String, int, String, Object)}。
     * 子类一般 override 类型化版本即可,无需 override 此 String 版本。
     */
    @Override
    public final String buildJdbcUrl(String host, int port, String database, String paramsJson) {
        P props = parseProps(paramsJson);
        return buildJdbcUrl(host, port, database, props);
    }

    protected String buildJdbcUrl(String host, int port, String database, P props) {
        return dbType().buildJdbcUrl(host, port, database);
    }

    /** 把 kv 以 {@code ?key=value&...} 风格追加到 URL。url 已有 ? 时用 & 衔接,同名参数后者覆盖前者。 */
    protected static String appendQuery(String url, Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean firstSep = !url.contains("?");
        for (Map.Entry<String, String> e : kv.entrySet()) {
            sb.append(firstSep ? '?' : '&').append(e.getKey()).append('=').append(e.getValue());
            firstSep = false;
        }
        return sb.toString();
    }

    /** Hive/Spark Beeline 风格:{@code ;key=value;...} 追加。url 末尾不带 ;。 */
    protected static String appendSemicolon(String url, Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        for (Map.Entry<String, String> e : kv.entrySet()) {
            sb.append(';').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** 默认探活 SQL。Hive/Spark 也用 SELECT 1。子类覆盖以适配 DB2(SYSIBM.SYSDUMMY1)/ Oracle(DUAL)。 */
    @Override
    public String getValidationQuery() {
        return "SELECT 1";
    }

    @Override
    public boolean isHasCatalog() {
        return dbType().isHasCatalog();
    }

    @Override
    public SqlDialect dialect() {
        // enum 名按约定与 SqlDialect 同名对齐;不一致由子类覆盖。
        return SqlDialect.of(dbType().name());
    }

    @Override
    public boolean validateConnection(Connection conn) throws SQLException {
        return conn.isValid(VALIDATE_TIMEOUT_SECONDS);
    }

    @Override
    public void applyStreamingFetch(Statement stmt) throws SQLException {
        // 默认 fetchSize=1000 兜底,与原 SqlExecutor.applyStreamingFetch 在 dialect==null 的行为一致。
        stmt.setFetchSize(DEFAULT_FETCH_SIZE);
    }

    @Override
    public String buildPreviewSql(String database, String table, int limit) {
        return "SELECT * FROM `" + database + "`.`" + table + "` LIMIT " + limit;
    }

    /** JSON → 强类型 P。空串返回 null,反序列化失败抛 IllegalArgumentException。 */
    protected final P parseProps(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.fromJson(json, propertiesClass());
    }

    /**
     * 把 properties record 的非空字段转成 {@code Map<String,String>},剔除 null,
     * 保留声明顺序。子类拼 URL query string 时调用。Jackson 会按 record component 顺序产出 LinkedHashMap。
     */
    protected final Map<String, String> nonNullFields(P props) {
        if (props == null) {
            return Map.of();
        }
        Map<String, Object> raw = JsonUtils.convertValue(props, new TypeReference<>() {
        });
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            String s = v == null ? null : Objects.toString(v, null);
            if (s != null && !s.isBlank()) {
                out.put(k, s);
            }
        });
        return out;
    }

    /**
     * 数据源 plugin params 走单个 rawJson entry — 前端渲染为 textarea,后端 strict Jackson 反序列化进
     * {@link #propertiesClass()}。子类 override {@link #defaultParamsJson()} 提供该 DB 的示例 JSON。
     */
    @Override
    public List<PluginParamDefinition> params() {
        return List.of(PluginParamDefinition.builder()
                .name("params")
                .type(PluginParamDefinition.TYPE_RAW_JSON)
                .defaultValue(defaultParamsJson())
                .build());
    }

    /** 该 DB 的示例 plugin params JSON,作为 textarea 的 placeholder。子类按需 override。 */
    protected String defaultParamsJson() {
        return "{}";
    }
}
