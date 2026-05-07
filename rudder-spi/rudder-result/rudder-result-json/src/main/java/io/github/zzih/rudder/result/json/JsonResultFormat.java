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

package io.github.zzih.rudder.result.json;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
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
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * JSONL 结果格式。第 1 行 _meta(columns 列表),后续每行一个 JSON 对象。
 * sidecar {@code .idx} 记每个数据行的字节起点(不含 _meta 行),read 用 idx O(1) seek。
 */
public class JsonResultFormat implements ResultFormat {

    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    @SuppressWarnings("unchecked")
    private static final IdxSidecar.ColumnsParser JSON_META_PARSER = line -> {
        Map<String, Object> meta = (Map<String, Object>) JsonUtils.fromJson(line, ROW_TYPE).get("_meta");
        return (List<String>) meta.get("columns");
    };

    @Override
    public String name() {
        return "json";
    }

    @Override
    public String extension() {
        return ".jsonl";
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
        String metaLine = JsonUtils.toJson(Map.of("_meta", Map.of("columns", columns)));
        w.write(metaLine);
        w.write('\n');
        long firstRowByte = metaLine.getBytes(StandardCharsets.UTF_8).length + 1L;
        return new IndexedJsonRowWriter(w, localFile, firstRowByte);
    }

    @Override
    public ResultPage read(FileStorage storage, String path, int offset, int limit) throws IOException {
        IdxSidecar.IdxData idx = IdxSidecar.loadCached(storage, path, JSON_META_PARSER);
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
                rows.add(JsonUtils.fromJson(line, ROW_TYPE));
            }
            return new ResultPage(idx.columns(), rows, total);
        }
    }

    @Override
    public long rowCount(FileStorage storage, String path) throws IOException {
        return IdxSidecar.loadCached(storage, path, JSON_META_PARSER).rowOffsets().length;
    }

    static class IndexedJsonRowWriter implements RowWriter {

        private final BufferedWriter w;
        private final DataOutputStream idxOut;
        private long byteCount;

        IndexedJsonRowWriter(BufferedWriter w, Path localFile, long firstRowByte) throws IOException {
            this.w = w;
            this.byteCount = firstRowByte;
            Path idxFile = localFile.resolveSibling(localFile.getFileName() + ".idx");
            this.idxOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(idxFile)));
        }

        @Override
        public void writeRow(Map<String, Object> row) throws IOException {
            idxOut.writeLong(byteCount);
            String line = JsonUtils.toJson(row);
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
