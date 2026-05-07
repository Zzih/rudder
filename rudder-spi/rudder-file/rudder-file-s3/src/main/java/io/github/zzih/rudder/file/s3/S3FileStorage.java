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

package io.github.zzih.rudder.file.s3;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.FileStorageUtils;
import io.github.zzih.rudder.file.api.StorageEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Slf4j
public class S3FileStorage implements FileStorage {

    private final String bucket;
    private final String basePath;
    private final S3Client s3;

    public S3FileStorage(String region, String accessKeyId, String secretAccessKey,
                         String bucket, String basePath) {
        this.bucket = bucket;
        this.basePath = FileStorageUtils.stripSlashes(basePath);
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    @Override
    public String getProvider() {
        return "S3";
    }

    @Override
    public String resolveFullPath(String path) {
        return "s3://" + bucket + "/" + basePath + "/" + FileStorage.normalizePath(path);
    }

    @Override
    public String upload(String path, InputStream input) {
        try {
            String key = toKey(path);
            byte[] bytes = input.readAllBytes();
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromBytes(bytes));
            log.debug("Uploaded file to s3://{}/{}", bucket, key);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file: " + path, e);
        }
    }

    @Override
    public InputStream download(String path) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(toKey(path)).build());
    }

    @Override
    public boolean delete(String path) {
        try {
            String key = toKey(path);
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            deleteByPrefix(key.endsWith("/") ? key : key + "/");
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(toKey(path)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void mkdir(String path) {
        String key = toKey(path);
        if (!key.endsWith("/")) {
            key = key + "/";
        }
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.empty());
    }

    @Override
    public List<String> list(String prefix) {
        String keyPrefix = ensureTrailingSlash(toKey(prefix != null ? prefix : ""));
        List<String> result = new ArrayList<>();
        String token = null;
        do {
            var reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(keyPrefix).delimiter("/");
            if (token != null) {
                reqBuilder.continuationToken(token);
            }
            ListObjectsV2Response resp = s3.listObjectsV2(reqBuilder.build());

            for (CommonPrefix cp : resp.commonPrefixes()) {
                result.add(toRelativePath(cp.prefix()));
            }
            for (S3Object obj : resp.contents()) {
                if (!obj.key().equals(keyPrefix)) {
                    result.add(toRelativePath(obj.key()));
                }
            }
            token = resp.nextContinuationToken();
        } while (token != null);
        return result;
    }

    @Override
    public List<StorageEntity> listEntities(String path) {
        String keyPrefix = ensureTrailingSlash(toKey(path != null ? path : ""));
        List<StorageEntity> result = new ArrayList<>();
        String token = null;
        do {
            var reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(keyPrefix).delimiter("/");
            if (token != null) {
                reqBuilder.continuationToken(token);
            }
            ListObjectsV2Response resp = s3.listObjectsV2(reqBuilder.build());

            String parentPath = toRelativePath(keyPrefix);
            if (parentPath.isEmpty()) {
                parentPath = "/";
            }

            for (CommonPrefix cp : resp.commonPrefixes()) {
                String relPath = toRelativePath(cp.prefix());
                result.add(buildDirEntity(relPath, parentPath));
            }
            for (S3Object obj : resp.contents()) {
                if (!obj.key().equals(keyPrefix)) {
                    result.add(buildFileEntity(obj));
                }
            }
            token = resp.nextContinuationToken();
        } while (token != null);
        return result;
    }

    @Override
    public StorageEntity getEntity(String path) {
        try {
            String key = toKey(path);
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            String relPath = toRelativePath(key);
            String fileName = FileStorageUtils.extractFileName(relPath);
            boolean isDir = key.endsWith("/");
            LocalDateTime lastMod = head.lastModified() != null
                    ? LocalDateTime.ofInstant(head.lastModified(), ZoneId.systemDefault())
                    : null;
            return StorageEntity.builder()
                    .fullName(relPath)
                    .fileName(fileName)
                    .parentPath(FileStorageUtils.extractParentPath(relPath))
                    .directory(isDir)
                    .size(isDir ? 0 : head.contentLength())
                    .createTime(lastMod)
                    .updateTime(lastMod)
                    .extension(FileStorageUtils.extractExtension(fileName))
                    .build();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void rename(String oldPath, String newPath) {
        String srcKey = toKey(oldPath);
        String dstKey = toKey(newPath);
        // S3 has no native rename — copy then delete
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(srcKey)
                .destinationBucket(bucket).destinationKey(dstKey).build());
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(srcKey).build());
    }

    @Override
    public String readContent(String path, int skipLines, int limit) {
        try (InputStream is = download(path)) {
            return FileStorageUtils.readLines(is, skipLines, limit);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content: " + path, e);
        }
    }

    @Override
    public void writeContent(String path, String content) {
        String key = toKey(path);
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType("text/plain; charset=utf-8").build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8));
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
            var reqBuilder = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
            if (token != null) {
                reqBuilder.continuationToken(token);
            }
            ListObjectsV2Response resp = s3.listObjectsV2(reqBuilder.build());

            List<ObjectIdentifier> ids = resp.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();
            if (!ids.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(ids).build())
                        .build());
            }
            token = resp.isTruncated() ? resp.nextContinuationToken() : null;
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

    private StorageEntity buildFileEntity(S3Object obj) {
        String relPath = toRelativePath(obj.key());
        String fileName = FileStorageUtils.extractFileName(obj.key());
        LocalDateTime lastMod = obj.lastModified() != null
                ? LocalDateTime.ofInstant(obj.lastModified(), ZoneId.systemDefault())
                : null;
        return StorageEntity.builder()
                .fullName(relPath)
                .fileName(fileName)
                .parentPath(FileStorageUtils.extractParentPath(relPath))
                .directory(false)
                .size(obj.size())
                .createTime(lastMod)
                .updateTime(lastMod)
                .extension(FileStorageUtils.extractExtension(fileName))
                .build();
    }
}
