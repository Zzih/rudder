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

package io.github.zzih.rudder.file.oss;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.FileStorageUtils;
import io.github.zzih.rudder.file.api.StorageEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OssFileStorage implements FileStorage {

    private final String bucket;
    private final String basePath;
    private final OSS ossClient;

    public OssFileStorage(String endpoint, String accessKeyId, String accessKeySecret,
                          String bucket, String basePath) {
        this.bucket = bucket;
        this.basePath = FileStorageUtils.stripSlashes(basePath);
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    @Override
    public String getProvider() {
        return "OSS";
    }

    @Override
    public String resolveFullPath(String path) {
        return "oss://" + bucket + "/" + basePath + "/" + FileStorage.normalizePath(path);
    }

    @Override
    public String upload(String path, InputStream input) {
        try {
            String key = toKey(path);
            ossClient.putObject(bucket, key, input);
            log.debug("Uploaded file to oss://{}/{}", bucket, key);
            return path;
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(new IOException("Failed to upload file: " + path, e));
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            return ossClient.getObject(bucket, toKey(path)).getObjectContent();
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(new IOException("Failed to download file: " + path, e));
        }
    }

    @Override
    public boolean delete(String path) {
        try {
            String key = toKey(path);
            deleteByPrefix(key.endsWith("/") ? key : key + "/");
            ossClient.deleteObject(bucket, key);
            return true;
        } catch (OSSException | ClientException e) {
            log.warn("Failed to delete: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return ossClient.doesObjectExist(bucket, toKey(path));
        } catch (OSSException | ClientException e) {
            log.warn("Failed to check existence: {}", path, e);
            return false;
        }
    }

    @Override
    public void mkdir(String path) {
        try {
            String key = toKey(path);
            if (!key.endsWith("/")) {
                key = key + "/";
            }
            ossClient.putObject(bucket, key, new ByteArrayInputStream(new byte[0]));
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(new IOException("Failed to create directory: " + path, e));
        }
    }

    @Override
    public List<String> list(String prefix) {
        try {
            String dirKey = ensureTrailingSlash(toKey(prefix != null ? prefix : ""));
            List<String> result = new ArrayList<>();
            String token = null;
            do {
                ListObjectsV2Request req = new ListObjectsV2Request(bucket);
                req.setPrefix(dirKey);
                req.setDelimiter("/");
                req.setMaxKeys(1000);
                if (token != null) {
                    req.setContinuationToken(token);
                }
                ListObjectsV2Result listing = ossClient.listObjectsV2(req);

                for (String cp : listing.getCommonPrefixes()) {
                    result.add(toRelativePath(cp));
                }
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    if (!summary.getKey().equals(dirKey)) {
                        result.add(toRelativePath(summary.getKey()));
                    }
                }
                token = listing.isTruncated() ? listing.getNextContinuationToken() : null;
            } while (token != null);
            return result;
        } catch (OSSException | ClientException e) {
            log.warn("Failed to list files under: {}", prefix, e);
            return List.of();
        }
    }

    @Override
    public List<StorageEntity> listEntities(String path) {
        try {
            String dirKey = ensureTrailingSlash(toKey(path != null ? path : ""));
            List<StorageEntity> result = new ArrayList<>();
            String token = null;
            do {
                ListObjectsV2Request req = new ListObjectsV2Request(bucket);
                req.setPrefix(dirKey);
                req.setDelimiter("/");
                req.setMaxKeys(1000);
                if (token != null) {
                    req.setContinuationToken(token);
                }
                ListObjectsV2Result listing = ossClient.listObjectsV2(req);

                String parentPath = toRelativePath(dirKey);
                if (parentPath.isEmpty()) {
                    parentPath = "/";
                }

                for (String cp : listing.getCommonPrefixes()) {
                    String relPath = toRelativePath(cp);
                    result.add(buildDirEntity(relPath, parentPath));
                }
                for (OSSObjectSummary summary : listing.getObjectSummaries()) {
                    if (!summary.getKey().equals(dirKey)) {
                        result.add(buildFileEntity(summary));
                    }
                }
                token = listing.isTruncated() ? listing.getNextContinuationToken() : null;
            } while (token != null);
            return result;
        } catch (OSSException | ClientException e) {
            log.warn("Failed to list entities under: {}", path, e);
            return List.of();
        }
    }

    @Override
    public StorageEntity getEntity(String path) {
        try {
            String key = toKey(path);
            var metadata = ossClient.getObjectMetadata(bucket, key);
            String relPath = toRelativePath(key);
            String fileName = FileStorageUtils.extractFileName(relPath);
            boolean isDir = key.endsWith("/");
            LocalDateTime updateTime = metadata.getLastModified() != null
                    ? LocalDateTime.ofInstant(metadata.getLastModified().toInstant(), ZoneId.systemDefault())
                    : null;
            return StorageEntity.builder()
                    .fullName(relPath)
                    .fileName(fileName)
                    .parentPath(FileStorageUtils.extractParentPath(relPath))
                    .directory(isDir)
                    .size(metadata.getContentLength())
                    .updateTime(updateTime)
                    .extension(FileStorageUtils.extractExtension(fileName))
                    .build();
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return null;
            }
            log.warn("Failed to get entity: {}", path, e);
            return null;
        } catch (ClientException e) {
            log.warn("Failed to get entity: {}", path, e);
            return null;
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        try {
            String srcKey = toKey(oldPath);
            String dstKey = toKey(newPath);
            ossClient.copyObject(new CopyObjectRequest(bucket, srcKey, bucket, dstKey));
            ossClient.deleteObject(bucket, srcKey);
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(
                    new IOException("Failed to rename: " + oldPath + " -> " + newPath, e));
        }
    }

    @Override
    public String readContent(String path, int skipLines, int limit) {
        try (InputStream is = ossClient.getObject(bucket, toKey(path)).getObjectContent()) {
            return FileStorageUtils.readLines(is, skipLines, limit);
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(new IOException("Failed to read content: " + path, e));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content: " + path, e);
        }
    }

    @Override
    public void writeContent(String path, String content) {
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ossClient.putObject(bucket, toKey(path), new ByteArrayInputStream(bytes));
        } catch (OSSException | ClientException e) {
            throw new UncheckedIOException(new IOException("Failed to write content: " + path, e));
        }
    }

    private String toKey(String path) {
        return FileStorageUtils.toObjectKey(basePath, path);
    }

    private String toRelativePath(String key) {
        return FileStorageUtils.toRelativePath(basePath, key);
    }

    private static String ensureTrailingSlash(String key) {
        return key.endsWith("/") ? key : key + "/";
    }

    private void deleteByPrefix(String prefix) {
        String token = null;
        do {
            ListObjectsV2Request req = new ListObjectsV2Request(bucket);
            req.setPrefix(prefix);
            req.setMaxKeys(1000);
            if (token != null) {
                req.setContinuationToken(token);
            }
            ListObjectsV2Result result = ossClient.listObjectsV2(req);
            for (OSSObjectSummary summary : result.getObjectSummaries()) {
                ossClient.deleteObject(bucket, summary.getKey());
            }
            token = result.isTruncated() ? result.getNextContinuationToken() : null;
        } while (token != null);
    }

    private StorageEntity buildDirEntity(String relativePath, String parentPath) {
        return StorageEntity.builder()
                .fullName(relativePath)
                .fileName(FileStorageUtils.extractFileName(relativePath))
                .parentPath(parentPath)
                .directory(true)
                .size(0)
                .extension("")
                .build();
    }

    private StorageEntity buildFileEntity(OSSObjectSummary summary) {
        String relPath = toRelativePath(summary.getKey());
        String fileName = FileStorageUtils.extractFileName(summary.getKey());
        LocalDateTime updateTime = summary.getLastModified() != null
                ? LocalDateTime.ofInstant(summary.getLastModified().toInstant(), ZoneId.systemDefault())
                : null;
        return StorageEntity.builder()
                .fullName(relPath)
                .fileName(fileName)
                .parentPath(FileStorageUtils.extractParentPath(relPath))
                .directory(false)
                .size(summary.getSize())
                .updateTime(updateTime)
                .extension(FileStorageUtils.extractExtension(fileName))
                .build();
    }
}
