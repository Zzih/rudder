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

package io.github.zzih.rudder.result.parquet;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultPage;
import io.github.zzih.rudder.result.api.RowWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.util.HadoopInputFile;

public class ParquetResultFormat implements ResultFormat {

    private static final Configuration CONF = new Configuration();
    static {
        CONF.set("fs.file.impl", org.apache.hadoop.fs.RawLocalFileSystem.class.getName());
        CONF.setBoolean("fs.file.impl.disable.cache", true);
        CONF.set("fs.defaultFS", "file:///");
    }

    @Override
    public String name() {
        return "parquet";
    }
    @Override
    public String extension() {
        return ".parquet";
    }

    @Override
    public RowWriter openWriter(Path localFile, List<String> columns) throws IOException {
        if (localFile.getParent() != null) {
            Files.createDirectories(localFile.getParent());
        }
        Files.deleteIfExists(localFile); // ParquetWriter 需要文件不存在
        Schema schema = buildSchema(columns);
        org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(localFile.toUri());
        ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(hp)
                .withSchema(schema).withConf(CONF).build();
        return new RowWriter() {

            @Override
            public void writeRow(Map<String, Object> row) throws IOException {
                GenericRecord record = new GenericData.Record(schema);
                for (String col : columns) {
                    Object v = row.get(col);
                    record.put(sanitize(col), v != null ? v.toString() : null);
                }
                writer.write(record);
            }

            @Override
            public void close() throws IOException {
                writer.close();
            }
        };
    }

    @Override
    public ResultPage read(FileStorage storage, String path, int offset, int limit) throws IOException {
        Path tmp = downloadToTemp(storage, path);
        try {
            org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(tmp.toUri());
            long totalRows = countRows(tmp);

            try (
                    ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(
                            HadoopInputFile.fromPath(hp, CONF)).build()) {
                GenericRecord first = reader.read();
                if (first == null) {
                    return new ResultPage(List.of(), List.of(), 0);
                }
                List<String> columns = first.getSchema().getFields().stream().map(Schema.Field::name).toList();

                List<Map<String, Object>> rows = new ArrayList<>();
                int idx = 0;
                if (idx >= offset && idx < offset + limit) {
                    rows.add(toMap(first, columns));
                }
                idx++;

                GenericRecord r;
                while ((r = reader.read()) != null) {
                    if (idx >= offset && idx < offset + limit) {
                        rows.add(toMap(r, columns));
                    }
                    idx++;
                    if (idx >= offset + limit) {
                        break;
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
            return countRows(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private long countRows(Path tmp) throws IOException {
        org.apache.hadoop.fs.Path hp = new org.apache.hadoop.fs.Path(tmp.toUri());
        try (ParquetFileReader r = ParquetFileReader.open(HadoopInputFile.fromPath(hp, CONF))) {
            return r.getFooter().getBlocks().stream().mapToLong(b -> b.getRowCount()).sum();
        }
    }

    private Path downloadToTemp(FileStorage storage, String path) throws IOException {
        Path tmp = Files.createTempFile("rudder-result-", ".parquet");
        try (InputStream is = storage.download(path); OutputStream os = Files.newOutputStream(tmp)) {
            is.transferTo(os);
        }
        return tmp;
    }

    private Map<String, Object> toMap(GenericRecord r, List<String> cols) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String c : cols) {
            Object v = r.get(c);
            m.put(c, v != null ? v.toString() : null);
        }
        return m;
    }

    private Schema buildSchema(List<String> columns) {
        List<Schema.Field> fields = new ArrayList<>();
        Schema ns = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
        for (String col : columns) {
            fields.add(new Schema.Field(sanitize(col), ns, null, Schema.Field.NULL_DEFAULT_VALUE));
        }
        return Schema.createRecord("Result", null, "io.github.zzih.rudder.result", false, fields);
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
