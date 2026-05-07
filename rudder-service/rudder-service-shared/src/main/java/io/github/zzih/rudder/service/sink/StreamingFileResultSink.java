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
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.RowWriter;
import io.github.zzih.rudder.service.config.FileConfigService;
import io.github.zzih.rudder.service.redaction.RedactionService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 流式文件 sink:逐行脱敏 + 写本地文件,close 时 upload 到 FileStorage。
 * Worker 主路径,内存峰值 = {@link #BATCH_SIZE} 行 + 底层 ResultFormat writer buffer,与总行数无关。
 *
 * <p>跟 {@link CollectingResultSink} 同族(都 {@code extends AbstractRedactingSink}):
 * **sink 内部脱敏**,出 sink 的数据一律脱敏后。差别仅在数据流向 ——
 * Collecting 攒在内存交给调用方,Streaming 边写本地文件边 BATCH_SIZE 一批攒满即 flush。
 *
 * <p>脱敏批量化:{@link RedactionService#applyMapRows} 内部每次调用都会 build 一次 ColumnPlan
 * (列 → 规则映射),逐行调用 N 次会重算 N 次 plan。这里攒 buffer 后一次性脱敏,把 plan
 * 计算成本摊到每批一次,在 100w 行的场景能省掉 99% 的 plan 重算开销。
 */
@Slf4j
public class StreamingFileResultSink extends AbstractRedactingSink {

    /** 脱敏批量化窗口。100 行内存可控,且把 buildPlan 成本摊到 1% */
    private static final int BATCH_SIZE = 100;

    private final FileConfigService fileConfig;
    private final ResultFormat format;
    private final String localDir;
    private final String storagePath;

    private RowWriter writer;
    private Path localFile;
    private boolean closed = false;

    private final List<Map<String, Object>> buffer = new ArrayList<>(BATCH_SIZE);

    /**
     * @param storagePath FileStorage 上的相对路径,由调用方按场景构造:
     *                    Worker 用 {@code StoragePathUtils.resultPathFromLogPath(logPath, ext)};
     *                    下载导出用 {@code "exports/{ts}-{user}.csv"} 之类。
     */
    public StreamingFileResultSink(RedactionService redactionService, FileConfigService fileConfig,
                                   ResultFormat format, String localDir, String storagePath) {
        super(redactionService);
        this.fileConfig = fileConfig;
        this.format = format;
        this.localDir = localDir;
        this.storagePath = storagePath;
    }

    @Override
    public void init(List<ColumnMeta> columnMetas) {
        super.init(columnMetas);
        if (this.columnMetas.isEmpty()) {
            return;
        }
        try {
            this.localFile = Paths.get(localDir, storagePath);
            List<String> columnNames = this.columnMetas.stream().map(ColumnMeta::getName).toList();
            this.writer = format.openWriter(localFile, columnNames);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open result writer: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(Map<String, Object> row) {
        if (writer == null) {
            return;
        }
        recordRow(row);
        buffer.add(row);
        if (buffer.size() >= BATCH_SIZE) {
            flushBuffer();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (writer == null) {
            return;
        }
        flushBuffer();
        writer.close();
        try (InputStream in = Files.newInputStream(localFile)) {
            fileConfig.required().upload(storagePath, in);
        }
        // 按 format 自己声明的伴生文件后缀搬运 — sink 不关心是 idx / footer / 别的
        for (String suffix : format.sidecarSuffixes()) {
            Path sidecar = localFile.resolveSibling(localFile.getFileName() + suffix);
            if (Files.exists(sidecar)) {
                try (InputStream in = Files.newInputStream(sidecar)) {
                    fileConfig.required().upload(storagePath + suffix, in);
                }
            }
        }
        log.info("Result written: {} rows, format={}, path={}", rowCount, format.name(), storagePath);
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }
        redactBatch(buffer);
        try {
            for (Map<String, Object> r : buffer) {
                writer.writeRow(r);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write row: " + e.getMessage(), e);
        }
        buffer.clear();
    }

    @Override
    public String getResultPath() {
        return storagePath;
    }
}
