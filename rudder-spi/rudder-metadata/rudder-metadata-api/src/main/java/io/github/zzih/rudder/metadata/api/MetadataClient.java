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

package io.github.zzih.rudder.metadata.api;

import io.github.zzih.rudder.metadata.api.model.ColumnMeta;
import io.github.zzih.rudder.metadata.api.model.TableDetail;
import io.github.zzih.rudder.metadata.api.model.TableMeta;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.util.List;

/**
 * 元数据统一契约。**永远 3 层**:{@code catalog → database → table}。
 * <p>
 * 两层引擎(MySQL / Hive / Spark / Flink 等 {@code DataSourceInfo#hasCatalog=false} 的数据源)
 * 在所有带 catalog 的方法上传 {@code null} 即可,{@link #listCatalogs} 对它们返回空列表。
 * <p>
 * 三层引擎(StarRocks / Trino 等)调用方先 {@code listCatalogs} 取得 catalog 列表,再用 {@code catalog} 向下查 database/table。
 *
 * <p>所有方法的 {@link DataSourceInfo} 由调用方组装(业务层 datasource → SPI target),
 * provider 不再回调宿主拿 datasource 信息,SPI 契约层完全自包含。
 */
public interface MetadataClient extends AutoCloseable {

    /** 健康检查。默认 UNKNOWN（provider 未实现）。 */
    default io.github.zzih.rudder.spi.api.model.HealthStatus healthCheck() {
        return io.github.zzih.rudder.spi.api.model.HealthStatus.unknown();
    }

    @Override
    default void close() {
    }

    /**
     * 列出 catalog。两层引擎返回空列表,调用方应走 {@link #listDatabases} 并传 {@code catalog=null}。
     */
    List<String> listCatalogs(DataSourceInfo target);

    List<String> listDatabases(DataSourceInfo target, String catalog);

    List<TableMeta> listTables(DataSourceInfo target, String catalog, String database);

    List<ColumnMeta> listColumns(DataSourceInfo target, String catalog, String database, String table);

    TableDetail getTableDetail(DataSourceInfo target, String catalog, String database, String table);

    List<TableMeta> search(DataSourceInfo target, String keyword);
}
