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

package io.github.zzih.rudder.result.avro;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultPage;
import io.github.zzih.rudder.result.api.RowWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

public class AvroResultFormat implements ResultFormat {

    @Override
    public String name() {
        return "avro";
    }
    @Override
    public String extension() {
        return ".avro";
    }

    @Override
    public RowWriter openWriter(Path localFile, List<String> columns) throws IOException {
        if (localFile.getParent() != null) {
            Files.createDirectories(localFile.getParent());
        }
        Files.deleteIfExists(localFile);
        Schema schema = buildSchema(columns);
        DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema));
        writer.create(schema, localFile.toFile());
        return new RowWriter() {

            @Override
            public void writeRow(Map<String, Object> row) throws IOException {
                GenericRecord record = new GenericData.Record(schema);
                for (String col : columns) {
                    Object val = row.get(col);
                    record.put(sanitize(col), val != null ? val.toString() : null);
                }
                writer.append(record);
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
        try (DataFileReader<GenericRecord> reader = new DataFileReader<>(tmp.toFile(), new GenericDatumReader<>())) {
            List<String> columns = reader.getSchema().getFields().stream().map(Schema.Field::name).toList();
            List<Map<String, Object>> rows = new ArrayList<>();
            long totalRows = 0;
            int idx = 0;
            for (GenericRecord record : reader) {
                totalRows++;
                if (idx >= offset && idx < offset + limit) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : columns) {
                        Object v = record.get(col);
                        row.put(col, v != null ? v.toString() : null);
                    }
                    rows.add(row);
                }
                idx++;
            }
            return new ResultPage(columns, rows, totalRows);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public long rowCount(FileStorage storage, String path) throws IOException {
        Path tmp = downloadToTemp(storage, path);
        try (DataFileReader<GenericRecord> reader = new DataFileReader<>(tmp.toFile(), new GenericDatumReader<>())) {
            long count = 0;
            while (reader.hasNext()) {
                reader.next();
                count++;
            }
            return count;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Path downloadToTemp(FileStorage storage, String path) throws IOException {
        Path tmp = Files.createTempFile("rudder-result-", ".avro");
        try (InputStream is = storage.download(path); OutputStream os = Files.newOutputStream(tmp)) {
            is.transferTo(os);
        }
        return tmp;
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
