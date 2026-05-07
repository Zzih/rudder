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

package io.github.zzih.rudder.service.download;

import io.github.zzih.rudder.common.utils.io.CsvUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 开头打 UTF-8 BOM,Excel 双击直接识别中文 — 不带 BOM 的 UTF-8 在简中环境下会被 Excel 按 GBK
 * 解析导致乱码。
 */
public class CsvDownloadWriter implements ResultDownloadWriter {

    private final BufferedWriter writer;
    private List<String> columns;

    public CsvDownloadWriter(OutputStream out) {
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    @Override
    public void writeHeader(List<String> columns) throws IOException {
        this.columns = columns;
        writer.write('\uFEFF');
        writeLine(columns);
    }

    @Override
    public void writeRow(Map<String, Object> row) throws IOException {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            Object v = row.get(columns.get(i));
            writer.write(CsvUtils.escape(v == null ? "" : v.toString()));
        }
        writer.write('\n');
    }

    @Override
    public void close() throws IOException {
        // servlet 容器管 OutputStream 生命周期,这里只 flush,不传染 close
        writer.flush();
    }

    private void writeLine(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(CsvUtils.escape(cells.get(i)));
        }
        writer.write('\n');
    }
}
