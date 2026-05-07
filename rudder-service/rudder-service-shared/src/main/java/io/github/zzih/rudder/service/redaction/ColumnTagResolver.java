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

package io.github.zzih.rudder.service.redaction;

import java.util.List;

/**
 * 按 "数据源 + 表 + 列" 查询元数据平台上该列挂的 tag 列表。
 * <p>
 * 脱敏引擎在 TAG 规则前调用此接口拿 tag;实现应带缓存(否则每行每列一次 API 调用吃不消)。
 * 不接入元数据平台的环境注入 {@link #EMPTY},所有列 tag 为空,自动降级走 COLUMN + TEXT。
 */
public interface ColumnTagResolver {

    /**
     * @param datasourceName Rudder 里配置的数据源名(可为 null,例如 Worker 结果文件场景)
     * @param database       库名
     * @param table          表名
     * @param column         列名(Calcite 解析出的原始列名)
     * @return tag 字符串列表(如 "PII.Phone"),未打 tag 或查询失败返回空 list,不抛
     */
    List<String> getTags(String datasourceName, String database, String table, String column);

    /** 空实现,永远返回空 list。默认注入用。 */
    ColumnTagResolver EMPTY = (ds, db, t, c) -> List.of();
}
