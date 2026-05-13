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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.request.FileContentUpdateRequest;
import io.github.zzih.rudder.api.request.FileCreateRequest;
import io.github.zzih.rudder.api.request.FileMkdirRequest;
import io.github.zzih.rudder.api.request.FileRenameRequest;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.net.HttpUtils;
import io.github.zzih.rudder.file.api.StorageEntity;
import io.github.zzih.rudder.service.file.FileService;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@RequireViewer
public class FileController {

    private final FileService fileService;

    // --- 上传 ---

    @PostMapping("/upload")
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.UPLOAD, resourceType = AuditResourceType.FILE, description = "上传文件")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false, defaultValue = "") String currentDir) {
        String path = fileService.upload(file, currentDir);
        return Result.ok(path);
    }

    // --- 下载 ---

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path) {
        InputStream inputStream = fileService.download(path);
        InputStreamResource resource = new InputStreamResource(inputStream);
        String filename = path.substring(path.lastIndexOf('/') + 1);
        // URL 里携带 JWT：禁止 Referer 外泄、禁止任何层级缓存
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, HttpUtils.contentDispositionAttachment(filename))
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // --- 删除 ---

    @DeleteMapping
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.DELETE, resourceType = AuditResourceType.FILE, description = "删除文件/目录")
    public Result<Boolean> delete(@RequestParam String path) {
        return Result.ok(fileService.delete(path));
    }

    // --- 列表查询 ---

    @GetMapping
    public Result<List<StorageEntity>> list(@RequestParam(required = false, defaultValue = "") String path) {
        return Result.ok(fileService.listEntities(path));
    }

    // --- 目录 ---

    @PostMapping("/directory")
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.MKDIR, resourceType = AuditResourceType.FILE, description = "创建目录")
    public Result<Void> mkdir(@RequestBody FileMkdirRequest request) {
        fileService.mkdir(request.getPath() != null ? request.getPath() : "");
        return Result.ok();
    }

    // --- 重命名 ---

    @PutMapping("/rename")
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.RENAME, resourceType = AuditResourceType.FILE, description = "重命名/移动文件")
    public Result<Void> rename(@RequestBody FileRenameRequest request) {
        fileService.rename(request.getOldPath(), request.getNewPath());
        return Result.ok();
    }

    // --- 在线创建/编辑 ---

    @PostMapping("/online-create")
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.ONLINE_CREATE, resourceType = AuditResourceType.FILE, description = "在线创建文件")
    public Result<String> onlineCreate(@RequestBody FileCreateRequest request) {
        String path = fileService.onlineCreate(
                request.getFileName(),
                request.getCurrentDir(),
                request.getContent());
        return Result.ok(path);
    }

    @GetMapping("/content")
    public Result<String> readContent(@RequestParam String path,
                                      @RequestParam(defaultValue = "0") int skipLines,
                                      @RequestParam(defaultValue = "5000") int limit) {
        return Result.ok(fileService.readContent(path, skipLines, limit));
    }

    @PutMapping("/content")
    @RequireDeveloper
    @AuditLog(module = AuditModule.FILE, action = AuditAction.UPDATE_CONTENT, resourceType = AuditResourceType.FILE, description = "修改文件内容")
    public Result<Void> updateContent(@RequestBody FileContentUpdateRequest request) {
        fileService.updateContent(request.getPath(), request.getContent());
        return Result.ok();
    }

    @GetMapping("/editable-suffixes")
    public Result<Set<String>> editableSuffixes() {
        return Result.ok(fileService.getEditableSuffixes());
    }
}
