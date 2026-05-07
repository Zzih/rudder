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

import io.github.zzih.rudder.file.api.FileStorage;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行级 idx sidecar 通用读取/缓存。CSV / JSONL / 等行式 ResultFormat 都用 8 字节/行的 .idx
 * 文件做 random access seek;这里把"读 idx + 读 header 解析 columns + 进程内 LRU 缓存"
 * 三段统一,各 format 只提供"如何从数据文件第一行解出 columns"。
 */
public final class IdxSidecar {

    private static final int CACHE_MAX_ENTRIES = 256;

    /** 进程内 LRU,bounded 防止 path 集合无界增长(长跑 server 累计访问的不同 result 文件没有上限)。 */
    private static final Map<String, IdxData> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_MAX_ENTRIES, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, IdxData> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            });

    public record IdxData(List<String> columns, long[] rowOffsets) {
    }

    /** 数据文件第一行 → columns 列表;不同 format 编码不一样(CSV 拆 ","、JSON 解 _meta)。 */
    @FunctionalInterface
    public interface ColumnsParser {

        List<String> parse(String firstLine) throws IOException;
    }

    private IdxSidecar() {
    }

    public static IdxData loadCached(FileStorage storage, String path, ColumnsParser parser) throws IOException {
        IdxData hit = CACHE.get(path);
        if (hit != null) {
            return hit;
        }
        IdxData loaded = load(storage, path, parser);
        CACHE.put(path, loaded);
        return loaded;
    }

    private static IdxData load(FileStorage storage, String path, ColumnsParser parser) throws IOException {
        byte[] all;
        try (
                InputStream is = storage.download(path + ".idx");
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            all = bos.toByteArray();
        }
        if (all.length % 8 != 0) {
            throw new IOException("Corrupt idx (size=" + all.length + " not /8): " + path + ".idx");
        }
        ByteBuffer bb = ByteBuffer.wrap(all);
        long[] offsets = new long[all.length / 8];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = bb.getLong();
        }
        // columns 从数据文件第一行拿;只读 header 一行就 close,代价小
        List<String> columns;
        try (
                InputStream is = storage.download(path);
                BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String headerLine = r.readLine();
            if (headerLine == null) {
                throw new IOException("Empty data file: " + path);
            }
            columns = parser.parse(headerLine);
        }
        return new IdxData(columns, offsets);
    }
}
