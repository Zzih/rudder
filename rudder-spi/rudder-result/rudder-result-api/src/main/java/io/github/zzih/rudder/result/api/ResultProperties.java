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

package io.github.zzih.rudder.result.api;

/**
 * Result SPI 的 provider 配置。所有 5 个 provider(PARQUET/CSV/JSON/ORC/AVRO)共用本结构。
 *
 * <p>{@code defaultQueryRows} 是 SQL 任务 setMaxRows 默认值;空 / 非正 → 走 {@link #DEFAULT_QUERY_ROWS}。
 *
 * @param defaultQueryRows SQL 任务行数上限默认值,1 起。null → 1000
 */
public record ResultProperties(Integer defaultQueryRows) {

    public static final int DEFAULT_QUERY_ROWS = 1000;

    public ResultProperties {
        if (defaultQueryRows == null || defaultQueryRows < 1) {
            defaultQueryRows = DEFAULT_QUERY_ROWS;
        }
    }

    public static ResultProperties defaults() {
        return new ResultProperties(DEFAULT_QUERY_ROWS);
    }
}
