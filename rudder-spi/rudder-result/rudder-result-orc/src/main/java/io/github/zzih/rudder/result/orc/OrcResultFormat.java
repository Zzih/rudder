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

package io.github.zzih.rudder.result.orc;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultPage;
import io.github.zzih.rudder.result.api.RowWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;

public class OrcResultFormat implements ResultFormat {

    private static final Configuration CONF = new Configuration();
    private static final RawLocalFileSystem LOCAL_FS = new RawLocalFileSystem();

    static {
        CONF.set("fs.defaultFS", "file:///");
        try {
            LOCAL_FS.initialize(java.net.URI.create("file:///"), CONF);
        } catch (Exception ignored) {
        }
    }

    @Override
    public String name() {
        return "orc";
    }

    @Override
    public String extension() {
        return ".orc";
    }

    @Override
    public RowWriter openWriter(Path localFile, List<String> columns) throws IOException {
        if (localFile.getParent() != null) {
            Files.createDirectories(localFile.getParent());
        }
        Files.deleteIfExists(localFile);
        TypeDescription schema = buildSchema(columns);
        org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(localFile.toAbsolutePath().toString());
        org.apache.orc.Writer writer = OrcFile.createWriter(hp,
                OrcFile.writerOptions(CONF).setSchema(schema).fileSystem(LOCAL_FS));
        VectorizedRowBatch batch = schema.createRowBatch();
        return new RowWriter() {

            @Override
            public void writeRow(Map<String, Object> row) throws IOException {
                int idx = batch.size++;
                for (int i = 0; i < columns.size(); i++) {
                    BytesColumnVector col = (BytesColumnVector) batch.cols[i];
                    Object val = row.get(columns.get(i));
                    if (val != null) {
                        col.setVal(idx, val.toString().getBytes(StandardCharsets.UTF_8));
                    } else {
                        col.noNulls = false;
                        col.isNull[idx] = true;
                    }
                }
                if (batch.size == batch.getMaxSize()) {
                    writer.addRowBatch(batch);
                    batch.reset();
                }
            }

            @Override
            public void close() throws IOException {
                if (batch.size > 0) {
                    writer.addRowBatch(batch);
                }
                writer.close();
            }
        };
    }

    @Override
    public ResultPage read(FileStorage storage, String path, int offset, int limit) throws IOException {
        Path tmp = downloadToTemp(storage, path);
        try {
            org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(tmp.toAbsolutePath().toString());
            try (
                    org.apache.orc.Reader orcReader = OrcFile.createReader(hp,
                            OrcFile.readerOptions(CONF).filesystem(LOCAL_FS))) {
                TypeDescription schema = orcReader.getSchema();
                List<String> columns = schema.getFieldNames();
                long totalRows = orcReader.getNumberOfRows();
                List<Map<String, Object>> rows = new ArrayList<>();
                try (RecordReader rr = orcReader.rows()) {
                    VectorizedRowBatch batch = schema.createRowBatch();
                    int globalIdx = 0;
                    while (rr.nextBatch(batch)) {
                        for (int i = 0; i < batch.size; i++) {
                            if (globalIdx >= offset && globalIdx < offset + limit) {
                                Map<String, Object> row = new LinkedHashMap<>();
                                for (int c = 0; c < columns.size(); c++) {
                                    BytesColumnVector col = (BytesColumnVector) batch.cols[c];
                                    row.put(columns.get(c), (!col.noNulls && col.isNull[i]) ? null : col.toString(i));
                                }
                                rows.add(row);
                            }
                            globalIdx++;
                            if (globalIdx >= offset + limit) {
                                break;
                            }
                        }
                        if (globalIdx >= offset + limit) {
                            break;
                        }
                    }
                }
                return new ResultPage(columns, rows, totalRows);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public long rowCount(FileStorage storage, String path) throws IOException {
        Path tmp = downloadToTemp(storage, path);
        try {
            org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(tmp.toAbsolutePath().toString());
            try (
                    org.apache.orc.Reader r = OrcFile.createReader(hp,
                            OrcFile.readerOptions(CONF).filesystem(LOCAL_FS))) {
                return r.getNumberOfRows();
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Path downloadToTemp(FileStorage storage, String path) throws IOException {
        Path tmp = Files.createTempFile("rudder-result-", ".orc");
        try (InputStream is = storage.download(path); OutputStream os = Files.newOutputStream(tmp)) {
            is.transferTo(os);
        }
        return tmp;
    }

    private TypeDescription buildSchema(List<String> columns) {
        TypeDescription schema = TypeDescription.createStruct();
        for (String col : columns) {
            schema.addField(col, TypeDescription.createString());
        }
        return schema;
    }
}
