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

package io.github.zzih.rudder.service.coordination.cache;

/** MetadataCacheService 的 key 工厂。前缀以 datasourceName 起头,配合 invalidateByDatasource 按前缀清。 */
public final class MetadataCacheKeys {

    private MetadataCacheKeys() {
    }

    public static String catalogs(String datasourceName) {
        return datasourceName + ":catalogs";
    }

    public static String databases(String datasourceName, String catalog) {
        return datasourceName + ":" + seg(catalog) + ":databases";
    }

    public static String tables(String datasourceName, String catalog, String database) {
        return datasourceName + ":" + seg(catalog) + ":" + database + ":tables";
    }

    public static String columns(String datasourceName, String catalog, String database, String table) {
        return datasourceName + ":" + seg(catalog) + ":" + database + ":" + table + ":columns";
    }

    public static String tableDetail(String datasourceName, String catalog, String database, String table) {
        return datasourceName + ":" + seg(catalog) + ":" + database + ":" + table + ":detail";
    }

    public static String search(String datasourceName, String keyword) {
        return datasourceName + ":search:" + keyword;
    }

    /** 两层引擎 catalog=null 时占位,保证 key 段数一致。 */
    private static String seg(String catalog) {
        return catalog == null || catalog.isEmpty() ? "_" : catalog;
    }
}
