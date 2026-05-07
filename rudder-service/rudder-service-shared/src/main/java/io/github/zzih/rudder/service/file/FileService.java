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

package io.github.zzih.rudder.service.file;

import io.github.zzih.rudder.common.enums.error.FileErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.StorageEntity;
import io.github.zzih.rudder.service.config.FileConfigService;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024;
    private static final long MAX_EDITABLE_SIZE = 1024 * 1024; // 1MB
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final Set<String> EDITABLE_SUFFIXES = Set.of(
            "txt", "log", "sh", "bat", "conf", "cfg", "py", "java", "sql",
            "xml", "hql", "properties", "json", "yml", "yaml", "ini", "js",
            "ts", "md", "csv", "html", "css", "scss", "vue", "jsx", "tsx");

    private final FileConfigService fileConfigService;

    private FileStorage storage() {
        return fileConfigService.required();
    }

    // --- 上传 ---

    public String upload(MultipartFile file, String currentDir) {
        if (file.isEmpty()) {
            throw new BizException(FileErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(FileErrorCode.FILE_TOO_LARGE_UPLOAD);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "unnamed";
        }
        originalName = normalizeFileName(originalName);

        String dir = currentDir != null && !currentDir.isEmpty()
                ? normalizeRelativePath(currentDir, true)
                : LocalDate.now().format(DATE_FMT);
        String storagePath = dir + "/" + originalName;

        // 同名文件自动加序号：file.jar → file(1).jar
        if (storage().exists(storagePath)) {
            int dotIdx = originalName.lastIndexOf('.');
            String baseName = dotIdx > 0 ? originalName.substring(0, dotIdx) : originalName;
            String ext = dotIdx > 0 ? originalName.substring(dotIdx) : "";
            for (int seq = 1; storage().exists(storagePath); seq++) {
                storagePath = dir + "/" + baseName + "(" + seq + ")" + ext;
            }
        }

        try {
            storage().upload(storagePath, file.getInputStream());
            log.info("File uploaded: {} -> {}", originalName, storagePath);
            return storagePath;
        } catch (Exception e) {
            throw new BizException(FileErrorCode.UPLOAD_FAILED, e.getMessage());
        }
    }

    // --- 下载 ---

    public InputStream download(String path) {
        path = normalizeRelativePath(path, false);
        if (!storage().exists(path)) {
            throw new NotFoundException(FileErrorCode.FILE_NOT_FOUND, path);
        }
        return storage().download(path);
    }

    // --- 删除 ---

    public boolean delete(String path) {
        path = normalizeRelativePath(path, false);
        if (!storage().exists(path)) {
            throw new NotFoundException(FileErrorCode.FILE_NOT_FOUND, path);
        }
        boolean deleted = storage().delete(path);
        log.info("File deleted: {} result={}", path, deleted);
        return deleted;
    }

    // --- 列表查询 ---

    public List<StorageEntity> listEntities(String path) {
        return storage().listEntities(normalizeRelativePath(path, true));
    }

    // --- 目录 ---

    public void mkdir(String path) {
        path = normalizeRelativePath(path, false);
        if (storage().exists(path)) {
            throw new BizException(FileErrorCode.DIRECTORY_EXISTS, path);
        }
        storage().mkdir(path);
        log.info("Directory created: {}", path);
    }

    // --- 重命名 ---

    public void rename(String oldPath, String newPath) {
        oldPath = normalizeRelativePath(oldPath, false);
        newPath = normalizeRelativePath(newPath, false);
        if (!storage().exists(oldPath)) {
            throw new NotFoundException(FileErrorCode.RENAME_SOURCE_NOT_FOUND, oldPath);
        }
        if (storage().exists(newPath)) {
            throw new BizException(FileErrorCode.RENAME_TARGET_EXISTS, newPath);
        }
        storage().rename(oldPath, newPath);
        log.info("Renamed: {} -> {}", oldPath, newPath);
    }

    // --- 在线创建/编辑 ---

    public String onlineCreate(String fileName, String currentDir, String content) {
        fileName = normalizeFileName(fileName);
        String dir = normalizeRelativePath(currentDir, true);
        String path = dir.isEmpty() ? fileName : dir + "/" + fileName;
        if (storage().exists(path)) {
            throw new BizException(FileErrorCode.FILE_EXISTS, path);
        }
        storage().writeContent(path, content != null ? content : "");
        log.info("Online created: {}", path);
        return path;
    }

    public String readContent(String path, int skipLines, int limit) {
        path = normalizeRelativePath(path, false);
        if (!storage().exists(path)) {
            throw new NotFoundException(FileErrorCode.FILE_NOT_FOUND, path);
        }
        checkEditable(path);
        return storage().readContent(path, skipLines, limit);
    }

    public void updateContent(String path, String content) {
        path = normalizeRelativePath(path, false);
        if (!storage().exists(path)) {
            throw new NotFoundException(FileErrorCode.FILE_NOT_FOUND, path);
        }
        checkEditable(path);
        storage().writeContent(path, content);
        log.info("Content updated: {}", path);
    }

    public boolean isEditable(String path, long size) {
        return getExtension(path).map(EDITABLE_SUFFIXES::contains).orElse(false)
                && size < MAX_EDITABLE_SIZE;
    }

    public Set<String> getEditableSuffixes() {
        return EDITABLE_SUFFIXES;
    }

    private void checkEditable(String path) {
        boolean supported = getExtension(path).map(EDITABLE_SUFFIXES::contains).orElse(false);
        if (!supported) {
            throw new BizException(FileErrorCode.FILE_TYPE_NOT_EDITABLE, path);
        }
        StorageEntity entity = storage().getEntity(path);
        if (entity != null && entity.getSize() >= MAX_EDITABLE_SIZE) {
            throw new BizException(FileErrorCode.FILE_TOO_LARGE_FOR_EDITING);
        }
    }

    private java.util.Optional<String> getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            return java.util.Optional.of(path.substring(dot + 1).toLowerCase());
        }
        return java.util.Optional.empty();
    }

    private String normalizeFileName(String fileName) {
        String normalized = normalizeRelativePath(fileName, false);
        if (normalized.contains("/")) {
            throw new BizException(FileErrorCode.FILE_NAME_HAS_SEPARATOR);
        }
        return normalized;
    }

    private String normalizeRelativePath(String path, boolean allowEmpty) {
        if (path == null || path.isBlank()) {
            if (allowEmpty) {
                return "";
            }
            throw new BizException(FileErrorCode.PATH_REQUIRED);
        }
        String raw = FileStorage.normalizePath(path.trim().replace('\\', '/'));
        String[] segments = raw.split("/");
        List<String> cleanSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new BizException(FileErrorCode.INVALID_PATH, path);
            }
            cleanSegments.add(segment);
        }
        String normalized = String.join("/", cleanSegments);
        if (!allowEmpty && normalized.isEmpty()) {
            throw new BizException(FileErrorCode.PATH_REQUIRED);
        }
        return normalized;
    }
}
