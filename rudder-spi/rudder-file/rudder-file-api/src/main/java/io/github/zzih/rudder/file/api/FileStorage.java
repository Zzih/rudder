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

package io.github.zzih.rudder.file.api;

import java.io.InputStream;
import java.util.List;

public interface FileStorage extends AutoCloseable {

    /** 健康检查。默认 UNKNOWN（provider 未实现）。 */
    default io.github.zzih.rudder.spi.api.model.HealthStatus healthCheck() {
        return io.github.zzih.rudder.spi.api.model.HealthStatus.unknown();
    }

    @Override
    default void close() {
    }

    String getProvider();

    /**
     * Normalize a storage-relative path by stripping the leading '/' if present.
     * <p>
     * All {@code FileStorage} methods accept storage-relative paths (e.g. "jar/app.jar").
     * Callers may pass paths with or without a leading '/' — implementations MUST call
     * this method to normalize before resolving against the storage base directory.
     * <p>
     * This mirrors DolphinScheduler's {@code AbstractStorageOperator} pattern where
     * path normalization is centralized rather than duplicated in each implementation.
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * 将存储相对路径解析为引擎可直接访问的绝对路径。
     * <p>
     * 示例（输入 "jars/my-app.jar" 或 "/jars/my-app.jar" 均可）：
     *   → LOCAL: /data/rudder/storage/jars/my-app.jar
     *   → HDFS:  hdfs://namenode:8020/rudder/jars/my-app.jar
     *   → OSS:   oss://bucket/rudder/jars/my-app.jar
     *   → S3:    s3://bucket/rudder/jars/my-app.jar
     */
    String resolveFullPath(String path);

    // --- 基本增删改查 ---

    String upload(String path, InputStream input);

    InputStream download(String path);

    boolean delete(String path);

    boolean exists(String path);

    // --- 目录操作 ---

    void mkdir(String path);

    // --- 文件列表 ---

    List<String> list(String prefix);

    /**
     * 游标分页列举目录单层条目(子目录 + 文件)。
     *
     * @param path   目录相对路径
     * @param cursor 上一页返回的 {@code nextCursor};{@code null} / 空表示首页
     * @param limit  本页期望条目上限(各 provider 尽力遵守,对象存储以 maxKeys 近似)
     * @return 本页条目 + nextCursor(null 表示末页);游标语义对调用方不透明
     */
    StoragePage listEntities(String path, String cursor, int limit);

    StorageEntity getEntity(String path);

    // --- 重命名 ---

    void rename(String oldPath, String newPath);

    // --- 内容读写（文本文件） ---

    String readContent(String path, int skipLines, int limit);

    void writeContent(String path, String content);
}
