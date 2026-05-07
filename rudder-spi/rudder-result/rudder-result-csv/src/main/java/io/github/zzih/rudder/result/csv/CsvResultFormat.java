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

package io.github.zzih.rudder.result.csv;

import io.github.zzih.rudder.common.utils.io.CsvUtils;
import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.result.api.IdxSidecar;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultPage;
import io.github.zzih.rudder.result.api.RowWriter;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * CSV 结果格式。写入时落 sidecar {@code .idx}(每行 8 字节字节起点),读取时
 * idx 查表 + InputStream.skip → seek 到行,O(limit) 不再 O(offset)。
 */
public class CsvResultFormat implements ResultFormat {

    private static final IdxSidecar.ColumnsParser CSV_HEADER_PARSER =
            line -> Arrays.asList(line.split(",", -1));

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public String extension() {
        return ".csv";
    }

    @Override
    public List<String> sidecarSuffixes() {
        return List.of(".idx");
    }

    @Override
    public RowWriter openWriter(Path localFile, List<String> columns) throws IOException {
        if (localFile.getParent() != null) {
            Files.createDirectories(localFile.getParent());
        }
        BufferedWriter w = Files.newBufferedWriter(localFile, StandardCharsets.UTF_8);
        StringJoiner header = new StringJoiner(",");
        for (String c : columns) {
            header.add(CsvUtils.escape(c));
        }
        String headerLine = header.toString();
        w.write(headerLine);
        w.write('\n');
        long firstRowByte = headerLine.getBytes(StandardCharsets.UTF_8).length + 1L;
        return new IndexedCsvRowWriter(w, columns, localFile, firstRowByte);
    }

    @Override
    public ResultPage read(FileStorage storage, String path, int offset, int limit) throws IOException {
        IdxSidecar.IdxData idx = IdxSidecar.loadCached(storage, path, CSV_HEADER_PARSER);
        long total = idx.rowOffsets().length;
        if (offset >= total) {
            return new ResultPage(idx.columns(), List.of(), total);
        }
        int actualLimit = (int) Math.min(limit, total - offset);
        long startByte = idx.rowOffsets()[offset];

        try (InputStream is = storage.download(path)) {
            long skipped = 0;
            while (skipped < startByte) {
                long n = is.skip(startByte - skipped);
                if (n <= 0) {
                    break;
                }
                skipped += n;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            List<Map<String, Object>> rows = new ArrayList<>(actualLimit);
            for (int i = 0; i < actualLimit; i++) {
                String line = r.readLine();
                if (line == null) {
                    break;
                }
                rows.add(parseLine(line, idx.columns()));
            }
            return new ResultPage(idx.columns(), rows, total);
        }
    }

    @Override
    public long rowCount(FileStorage storage, String path) throws IOException {
        return IdxSidecar.loadCached(storage, path, CSV_HEADER_PARSER).rowOffsets().length;
    }

    private Map<String, Object> parseLine(String line, List<String> columns) {
        String[] values = line.split(",", -1);
        Map<String, Object> row = new LinkedHashMap<>();
        for (int j = 0; j < columns.size() && j < values.length; j++) {
            row.put(columns.get(j), unescapeCsv(values[j]));
        }
        return row;
    }

    private static String unescapeCsv(String v) {
        return (v.startsWith("\"") && v.endsWith("\""))
                ? v.substring(1, v.length() - 1).replace("\"\"", "\"")
                : v;
    }

    /**
     * close 时落 {@code localFile.idx},约定数据文件路径 + ".idx",由
     * {@link io.github.zzih.rudder.result.api.ResultFormat#sidecarSuffixes()} 暴露给
     * StreamingFileResultSink 一并 upload 到 FileStorage。
     */
    static class IndexedCsvRowWriter implements RowWriter {

        private final BufferedWriter w;
        private final List<String> columns;
        private final DataOutputStream idxOut;
        private long byteCount;

        IndexedCsvRowWriter(BufferedWriter w, List<String> columns, Path localFile, long firstRowByte)
                                                                                                       throws IOException {
            this.w = w;
            this.columns = columns;
            this.byteCount = firstRowByte;
            Path idxFile = localFile.resolveSibling(localFile.getFileName() + ".idx");
            this.idxOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(idxFile)));
        }

        @Override
        public void writeRow(Map<String, Object> row) throws IOException {
            idxOut.writeLong(byteCount);
            StringJoiner joiner = new StringJoiner(",");
            for (String col : columns) {
                Object val = row.get(col);
                joiner.add(CsvUtils.escape(val != null ? val.toString() : ""));
            }
            String line = joiner.toString();
            w.write(line);
            w.write('\n');
            byteCount += line.getBytes(StandardCharsets.UTF_8).length + 1L;
        }

        @Override
        public void close() throws IOException {
            try {
                w.close();
            } finally {
                idxOut.close();
            }
        }
    }
}
