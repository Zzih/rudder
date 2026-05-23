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

package io.github.zzih.rudder.common.sql;

import java.util.List;

/**
 * 任务执行预计要访问的资源 + 操作类型。
 *
 * <p>由 {@code DataPermAwareTask.resolveAccessIntent} 产出,Worker pipeline 在 task.handle() 前送 Local 鉴权;
 * 鉴权方按 (catalog, database, table) 找 Scope grant,跟 {@link Action} 比对 access 集合。
 *
 * <p>{@code columns} 空 = 表级访问(等价于"全部列");非空 = 列级访问。
 *
 * <p>{@code catalog} 空 = plugin 不支持 catalog 层(MySQL/Hive 两层引擎);非空 = 三层引擎(Trino/StarRocks)的 catalog。
 */
public record TableAccess(
        String catalog,
        String database,
        String table,
        List<String> columns,
        Action action) {

    public enum Action {
        READ,
        INSERT,
        UPDATE,
        DELETE,
        CREATE,
        DROP,
        ALTER
    }
}
