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

package io.github.zzih.rudder.file.hdfs;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.FileStorageUtils;
import io.github.zzih.rudder.file.api.StorageEntity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HdfsFileStorage implements FileStorage {

    private final String defaultFs;
    private final String basePath;
    private final FileSystem fs;

    public HdfsFileStorage(String defaultFs, String basePath) {
        this.defaultFs = defaultFs.endsWith("/") ? defaultFs.substring(0, defaultFs.length() - 1) : defaultFs;
        this.basePath = basePath.startsWith("/") ? basePath : "/" + basePath;
        try {
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", this.defaultFs);
            this.fs = FileSystem.get(conf);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize HDFS FileSystem: " + defaultFs, e);
        }
    }

    @Override
    public String getProvider() {
        return "HDFS";
    }

    @Override
    public String resolveFullPath(String path) {
        return defaultFs + basePath + "/" + FileStorage.normalizePath(path);
    }

    @Override
    public String upload(String path, InputStream input) {
        try {
            Path target = resolve(path);
            ensureParentDir(target);
            try (FSDataOutputStream out = fs.create(target, true)) {
                input.transferTo(out);
            }
            log.debug("Uploaded file to {}", target);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            return fs.open(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download file: " + path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        try {
            return fs.delete(resolve(path), true);
        } catch (IOException e) {
            log.warn("Failed to delete: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return fs.exists(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to check existence: " + path, e);
        }
    }

    @Override
    public void mkdir(String path) {
        try {
            fs.mkdirs(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + path, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        try {
            Path dir = resolve(prefix != null ? prefix : "");
            Path baseDir = new Path(basePath);
            FileStatus[] statuses = fs.listStatus(dir);
            List<String> result = new ArrayList<>(statuses.length);
            for (FileStatus status : statuses) {
                result.add(relativize(baseDir, status.getPath()));
            }
            return result;
        } catch (FileNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            log.warn("Failed to list files under: {}", prefix, e);
            return List.of();
        }
    }

    @Override
    public List<StorageEntity> listEntities(String path) {
        try {
            Path dir = resolve(path != null ? path : "");
            FileStatus[] statuses = fs.listStatus(dir);
            List<StorageEntity> result = new ArrayList<>(statuses.length);
            for (FileStatus status : statuses) {
                result.add(buildEntity(status));
            }
            return result;
        } catch (FileNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            log.warn("Failed to list entities under: {}", path, e);
            return List.of();
        }
    }

    @Override
    public StorageEntity getEntity(String path) {
        try {
            return buildEntity(fs.getFileStatus(resolve(path)));
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get entity: " + path, e);
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        try {
            Path source = resolve(oldPath);
            Path target = resolve(newPath);
            ensureParentDir(target);
            if (!fs.rename(source, target)) {
                throw new IOException("HDFS rename returned false");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rename: " + oldPath + " -> " + newPath, e);
        }
    }

    @Override
    public String readContent(String path, int skipLines, int limit) {
        try (InputStream in = fs.open(resolve(path))) {
            return FileStorageUtils.readLines(in, skipLines, limit);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content: " + path, e);
        }
    }

    @Override
    public void writeContent(String path, String content) {
        try {
            Path target = resolve(path);
            ensureParentDir(target);
            try (FSDataOutputStream out = fs.create(target, true)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write content: " + path, e);
        }
    }

    private Path resolve(String path) {
        return new Path(basePath + "/" + FileStorage.normalizePath(path));
    }

    private void ensureParentDir(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            fs.mkdirs(parent);
        }
    }

    private String relativize(Path base, Path child) {
        String baseStr = base.toUri().getPath();
        String childStr = child.toUri().getPath();
        if (childStr.startsWith(baseStr)) {
            String relative = childStr.substring(baseStr.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return relative;
        }
        return child.getName();
    }

    private StorageEntity buildEntity(FileStatus status) {
        Path filePath = status.getPath();
        Path baseDir = new Path(basePath);
        String relativePath = relativize(baseDir, filePath);
        String fileName = filePath.getName();
        String parentRelative = relativize(baseDir, filePath.getParent());
        LocalDateTime modTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(status.getModificationTime()), ZoneId.systemDefault());
        return StorageEntity.builder()
                .fullName(relativePath)
                .fileName(fileName)
                .parentPath(parentRelative.isEmpty() ? "/" : parentRelative)
                .directory(status.isDirectory())
                .size(status.isDirectory() ? 0 : status.getLen())
                .createTime(modTime)
                .updateTime(modTime)
                .extension(FileStorageUtils.extractExtension(fileName))
                .build();
    }
}
