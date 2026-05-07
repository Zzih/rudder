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

package io.github.zzih.rudder.service.sink;

import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.task.api.task.sink.ResultSink;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 所有 ResultSink 的脱敏脚手架。redact 是 sink 的内置义务,不是某种 sink 的"特点":
 * 数据流出 sink 时一律已经过 RedactionService(或 null 时跳过)。
 * 子类只决定"何时收行 / 何时把整批喂给 {@link #redactBatch}"。
 */
public abstract class AbstractRedactingSink implements ResultSink {

    /** null = 不脱敏,给"测试 / 内部 metadata"等不需要脱敏的内部路径用。 */
    protected final RedactionService redactionService;
    protected List<ColumnMeta> columnMetas = Collections.emptyList();
    protected long rowCount = 0;
    protected Map<String, Object> firstRow;

    protected AbstractRedactingSink(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    @Override
    public void init(List<ColumnMeta> columnMetas) {
        this.columnMetas = columnMetas != null ? columnMetas : Collections.emptyList();
    }

    @Override
    public long getRowCount() {
        return rowCount;
    }

    @Override
    public Map<String, Object> getFirstRow() {
        return firstRow;
    }

    @Override
    public List<ColumnMeta> getColumnMetas() {
        return columnMetas;
    }

    /**
     * 子类在收到行后调,自行决定 batch 时机。原地改写传入 list,调用前 list 是脱敏前,调用后是脱敏后。
     * 调用契约:每条出现在最终输出里的行,必须经过本方法**恰好一次**。redactionService=null 时直接 no-op。
     */
    protected final void redactBatch(List<Map<String, Object>> batch) {
        if (batch == null || batch.isEmpty() || redactionService == null) {
            return;
        }
        redactionService.applyMapRows(columnMetas, batch);
    }

    /** 子类的 write 实现里调,统一记一次行计数 + 首行截留。 */
    protected final void recordRow(Map<String, Object> row) {
        if (firstRow == null) {
            firstRow = row;
        }
        rowCount++;
    }
}
