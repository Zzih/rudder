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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class FileStorageUtils {

    private FileStorageUtils() {
    }

    /**
     * 去除首尾斜杠。用于 object storage 的 basePath 规范化。
     */
    public static String stripSlashes(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 从 object key 或路径中提取文件名（最后一段），自动去除尾部斜杠。
     */
    public static String extractFileName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String s = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    /**
     * 提取父路径，无父路径时返回 "/"。
     */
    public static String extractParentPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int idx = path.lastIndexOf('/');
        return idx <= 0 ? "/" : path.substring(0, idx);
    }

    /**
     * 提取文件扩展名（不含点），正确处理 dotfile（如 .gitignore 返回空）。
     */
    public static String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }

    /**
     * 从 InputStream 读取文本内容，支持跳过行和限制行数。
     */
    public static String readLines(InputStream is, int skipLines, int limit) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().skip(skipLines).limit(limit)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Object storage（S3/OSS）共用：将存储相对路径转换为 object key。
     */
    public static String toObjectKey(String basePath, String path) {
        String normalized = FileStorage.normalizePath(path);
        if (basePath.isEmpty()) {
            return normalized;
        }
        return normalized.isEmpty() ? basePath : basePath + "/" + normalized;
    }

    /**
     * Object storage（S3/OSS）共用：将 object key 转换为存储相对路径。
     */
    public static String toRelativePath(String basePath, String key) {
        String stripped = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        if (basePath.isEmpty()) {
            return stripped;
        }
        if (stripped.startsWith(basePath + "/")) {
            return stripped.substring(basePath.length() + 1);
        }
        if (stripped.equals(basePath)) {
            return "";
        }
        return stripped;
    }
}
