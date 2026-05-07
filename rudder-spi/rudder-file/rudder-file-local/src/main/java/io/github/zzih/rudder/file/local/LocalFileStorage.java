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

package io.github.zzih.rudder.file.local;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.StorageEntity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalFileStorage implements FileStorage {

    private final String basePath;
    private final Path baseDir;

    public LocalFileStorage(String basePath) {
        this.basePath = basePath;
        this.baseDir = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @Override
    public String getProvider() {
        return "LOCAL";
    }

    @Override
    public String resolveFullPath(String path) {
        return resolve(path).toAbsolutePath().toString();
    }

    @Override
    public String upload(String path, InputStream input) {
        try {
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Uploaded file to {}", target);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            return Files.newInputStream(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download file: " + path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        try {
            Path target = resolve(path);
            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
                return true;
            }
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public void mkdir(String path) {
        try {
            Files.createDirectories(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + path, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        Path dir = resolve(prefix != null ? prefix : "");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> baseDir.relativize(p).toString()).toList();
        } catch (IOException e) {
            log.warn("Failed to list files under: {}", prefix, e);
            return List.of();
        }
    }

    @Override
    public List<StorageEntity> listEntities(String path) {
        Path dir = resolve(path != null ? path : "");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> buildEntity(p, baseDir)).toList();
        } catch (IOException e) {
            log.warn("Failed to list entities under: {}", path, e);
            return List.of();
        }
    }

    @Override
    public StorageEntity getEntity(String path) {
        Path target = resolve(path);
        if (!Files.exists(target)) {
            return null;
        }
        return buildEntity(target, baseDir);
    }

    @Override
    public void rename(String oldPath, String newPath) {
        try {
            Path source = resolve(oldPath);
            Path target = resolve(newPath);
            Files.createDirectories(target.getParent());
            Files.move(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rename: " + oldPath + " -> " + newPath, e);
        }
    }

    @Override
    public String readContent(String path, int skipLines, int limit) {
        try {
            Path target = resolve(path);
            try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
                List<String> result = lines.skip(skipLines).limit(limit).toList();
                return String.join("\n", result);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content: " + path, e);
        }
    }

    @Override
    public void writeContent(String path, String content) {
        try {
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write content: " + path, e);
        }
    }

    private Path resolve(String path) {
        Path resolved = baseDir.resolve(FileStorage.normalizePath(path)).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path escapes storage base directory: " + path);
        }
        return resolved;
    }

    private StorageEntity buildEntity(Path p, Path base) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            String relativePath = base.relativize(p).toString();
            String fileName = p.getFileName().toString();
            String parentPath = base.relativize(p.getParent()).toString();
            String ext = "";
            if (!attrs.isDirectory() && fileName.contains(".")) {
                ext = fileName.substring(fileName.lastIndexOf('.') + 1);
            }
            return StorageEntity.builder()
                    .fullName(relativePath)
                    .fileName(fileName)
                    .parentPath(parentPath.isEmpty() ? "/" : parentPath)
                    .directory(attrs.isDirectory())
                    .size(attrs.isDirectory() ? 0 : attrs.size())
                    .createTime(LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()))
                    .updateTime(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()))
                    .extension(ext)
                    .build();
        } catch (IOException e) {
            log.warn("Failed to read attributes: {}", p, e);
            return StorageEntity.builder()
                    .fullName(base.relativize(p).toString())
                    .fileName(p.getFileName().toString())
                    .directory(Files.isDirectory(p))
                    .build();
        }
    }
}
