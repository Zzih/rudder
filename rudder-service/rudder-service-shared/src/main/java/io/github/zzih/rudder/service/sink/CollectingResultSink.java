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

import io.github.zzih.rudder.service.redaction.RedactionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 收集型 sink:把所有行攒到内部 {@link ArrayList},通过 {@link #getRows()} 暴露给调用方。
 * 用于 IDE 预览 / 数据源 readOnly / Runtime 内部 batch 等"必须把行返回给调用方"的小批量场景。
 * {@link #getResultPath()} 返回 null —— 不落盘。
 *
 * <p>跟 {@link StreamingFileResultSink} 同族(都 {@code extends AbstractRedactingSink}):
 * **sink 内部脱敏**,出 sink 的数据一律脱敏后。close 时一次性整批 apply,
 * 把 RedactionService 的 plan 计算成本摊到一次。
 *
 * <p>{@code redactionService=null} 时退化为不脱敏,专给"测试 / 内部 metadata"场景。
 * 业务路径(数据预览 / 数据源 readOnly)必须传真实 RedactionService。
 */
public class CollectingResultSink extends AbstractRedactingSink {

    private final List<Map<String, Object>> rows = new ArrayList<>();
    private boolean redacted = false;

    public CollectingResultSink(RedactionService redactionService) {
        super(redactionService);
    }

    @Override
    public void write(Map<String, Object> row) {
        recordRow(row);
        rows.add(row);
    }

    @Override
    public void close() {
        if (!redacted) {
            redactBatch(rows);
            redacted = true;
        }
    }

    @Override
    public String getResultPath() {
        return null;
    }

    /**
     * 调用方拉取脱敏后的全部行。如果 caller 没显式 close,这里兜底脱敏一次。
     * 返回不可变视图,防止 caller 误改 sink 内部状态。
     */
    public List<Map<String, Object>> getRows() {
        if (!redacted) {
            redactBatch(rows);
            redacted = true;
        }
        return Collections.unmodifiableList(rows);
    }
}
