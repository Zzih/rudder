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
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.datasource.DatasourceType;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据源类型 SPI 工厂。与 {@code FileStorageFactory} / {@code LlmClientFactory} 同档次,
 * 实现 Rudder 内部 SPI 标准 {@link ConfigurablePluginProviderFactory} 的全部生命周期:
 * <ul>
 *   <li>{@link #getProvider()} — provider 标识(MYSQL / HIVE / ...),默认走 {@link #dbType()}.name()</li>
 *   <li>{@link #type()} — SPI 大类,固定 {@code "datasource"}</li>
 *   <li>{@link #propertiesClass()} — 强类型 properties 类,DB 表 {@code params} JSON 据此反序列化</li>
 *   <li>{@link #params()} — 前端表单元数据,声明该 DB 的可调字段(useSSL/principal/sslmode 等)</li>
 *   <li>{@link #guide(java.util.Locale)} — Markdown 接入指南,默认从 classpath 加载</li>
 *   <li>{@link #validate(Object)} — 保存配置前的强类型字段校验</li>
 * </ul>
 *
 * <p>外加 datasource 专属的方言行为(原本散落 switch 收敛而来):
 * <ul>
 *   <li>{@link #dbType()} 与 {@link DatasourceType} 一一对应,Registry 按此分桶</li>
 *   <li>{@link #buildJdbcUrl} / {@link #getDriverClass} / {@link #isHasCatalog} 委托 enum</li>
 *   <li>{@link #validateConnection} — Flink JDBC 驱动用 SELECT 1,其余 isValid(5)</li>
 *   <li>{@link #applyStreamingFetch} — MySQL 家族用 MIN_VALUE,Postgres 用游标,其余 1000</li>
 *   <li>{@link #buildPreviewSql} — Hive/Spark/Trino 不带反引号,其余加</li>
 * </ul>
 *
 * @param <P> 该 DB 的强类型 properties POJO(record),代表前端表单 + DB JSON 存的字段集
 */
public interface DatasourceTypeProvider<P> extends ConfigurablePluginProviderFactory<ProviderContext, P> {

    /** 与 {@link DatasourceType} 一一对应,Registry 按此分桶。 */
    DatasourceType dbType();

    /** Provider 唯一标识。默认走 {@code dbType().name()}(MYSQL / HIVE / ...)。 */
    @Override
    default String getProvider() {
        return dbType().name();
    }

    /** SPI 大类:固定 {@code datasource},供 guide / metadata 找 md 文件用。 */
    @Override
    default String type() {
        return "datasource";
    }

    /** JDBC 驱动类全限定名。 */
    String getDriverClass();

    /**
     * 按 host/port/database + 强类型 properties 拼装 JDBC URL。plugin 负责把 properties 的字段
     * 拼到驱动**真正认识的位置**:
     * <ul>
     *   <li>MySQL 家族 / Postgres / Trino / ClickHouse: {@code jdbc:xxx://h:p/db?k=v&k=v}</li>
     *   <li>Hive / Spark Thrift: {@code jdbc:hive2://h:p/db;k=v;k=v}(分号分隔,Beeline 风格)</li>
     * </ul>
     *
     * <p>很多 JDBC 驱动只读 URL query string,**不读** {@code Properties} 对象,所以不能依赖
     * {@code HikariCP.addDataSourceProperty} —— 必须 plugin 自己拼到 URL 上。
     *
     * @param paramsJson {@code Datasource.params} 字段的原始 JSON。由基类 {@code AbstractJdbcTypeProvider}
     *                   反序列化成强类型 {@code P} 后,转调子类 typed 重载。
     */
    String buildJdbcUrl(String host, int port, String database, String paramsJson);

    /**
     * 探活 SQL。{@code HikariCP.setConnectionTestQuery} 用它做连接复用前的健康检查。
     * 大多 DB 走 {@code SELECT 1};DB2 走 {@code SELECT 1 FROM SYSIBM.SYSDUMMY1},
     * Oracle 走 {@code SELECT 1 FROM DUAL}。默认 {@code SELECT 1}。
     */
    String getValidationQuery();

    /** true = catalog.database.table 三层引擎(StarRocks/Trino);false = database.table。 */
    boolean isHasCatalog();

    /** SQL 血缘 / access intent 解析用的方言,与驱动行为无关。 */
    SqlDialect dialect();

    /**
     * 校验连接是否活着。默认实现走 {@code isValid(5)};Flink 等不支持的驱动需覆盖。
     *
     * @return true 表示活着,false 表示该断开
     */
    boolean validateConnection(Connection conn) throws SQLException;

    /**
     * 配置 Statement 走流式读,避免大结果集一次性吃满堆。具体策略由各 provider 决定:
     * <ul>
     *   <li>MySQL 家族(MySQL/Doris/StarRocks):{@code setFetchSize(Integer.MIN_VALUE)}</li>
     *   <li>Postgres:连接需 {@code autoCommit=false} + 游标 fetchSize</li>
     *   <li>其余:统一 {@code setFetchSize(1000)}</li>
     * </ul>
     */
    void applyStreamingFetch(Statement stmt) throws SQLException;

    /**
     * 拼装预览 SQL(SELECT * + LIMIT)。Hive/Spark/Trino 走无反引号路径,其余走反引号路径。
     * 调用方负责 identifier 合法性校验(防 SQL 注入)。
     */
    String buildPreviewSql(String database, String table, int limit);
}
