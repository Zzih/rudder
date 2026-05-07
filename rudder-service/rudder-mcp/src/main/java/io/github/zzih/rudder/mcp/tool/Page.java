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

package io.github.zzih.rudder.mcp.tool;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Data;

/**
 * MCP tool 通用分页结果。{@code rudder-common} 的 {@code PageResult} 带 code/message/timestamp
 * 三段式 REST envelope，不适合 MCP 工具直接返回；这里只暴露纯数据字段。
 *
 * <p>同样隐藏 MyBatis-Plus {@code IPage} 的 ORM 内部字段（current/size/orders/searchCount/countId 等）。
 */
@Data
public class Page<T> {

    @JsonPropertyDescription("Total number of records matching the query (not just current page)")
    private long total;

    @JsonPropertyDescription("Current page number (1-based)")
    private int pageNum;

    @JsonPropertyDescription("Page size")
    private int pageSize;

    @JsonPropertyDescription("Records on the current page")
    private List<T> records;

    public static <T> Page<T> of(long total, int pageNum, int pageSize, List<T> records) {
        Page<T> p = new Page<>();
        p.setTotal(total);
        p.setPageNum(pageNum);
        p.setPageSize(pageSize);
        p.setRecords(records);
        return p;
    }
}
