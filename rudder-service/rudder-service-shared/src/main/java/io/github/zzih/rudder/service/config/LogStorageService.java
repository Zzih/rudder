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

package io.github.zzih.rudder.service.config;

import io.github.zzih.rudder.common.execution.LogResponse;
import io.github.zzih.rudder.common.utils.crypto.PathSecurityUtils;
import io.github.zzih.rudder.file.api.FileStorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志文件 IO 工具：负责本地缓存目录管理、上传 FileStorage、流式读取。
 * <p>
 * 为什么独立成 service：编排端（WorkflowInstanceRunner）和执行端（LogServiceImpl）都要做这些事，
 * 不应该让编排端反过来依赖执行端的 RPC 实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogStorageService {

    /** 单次响应返回的最大行数上限，防止大日志 OOM；客户端下一轮轮询用新的 offset 继续拉。 */
    private static final int MAX_LINES_PER_RESPONSE = 10_000;

    /** 单次响应返回的最大字节数上限（约 4 MB），双重保护超长行场景。 */
    private static final int MAX_BYTES_PER_RESPONSE = 4 * 1024 * 1024;

    private final FileConfigService fileConfigService;

    @Value("${rudder.file.local-dir:./data}")
    private String localDir;

    private Path baseDir;

    @PostConstruct
    void init() {
        this.baseDir = Paths.get(localDir).toAbsolutePath().normalize();
    }

    /** storage 相对路径 → 本地绝对路径，拒绝路径穿越。 */
    public String toLocalPath(String storagePath) {
        return PathSecurityUtils.resolveWithinBase(baseDir, storagePath).toString();
    }

    /** 确保本地目录存在。 */
    public void ensureLogDir(String storagePath) {
        Path localPath = Paths.get(toLocalPath(storagePath));
        try {
            Files.createDirectories(localPath.getParent());
        } catch (IOException e) {
            log.error("Failed to create log dir: {}", localPath.getParent(), e);
        }
    }

    /** 将本地日志上传到 FileStorage。 */
    public void uploadLog(String storagePath) {
        if (storagePath == null) {
            return;
        }
        Path localPath = Paths.get(toLocalPath(storagePath));
        if (!Files.exists(localPath)) {
            return;
        }
        try {
            fileConfigService.required().upload(storagePath, Files.newInputStream(localPath));
            log.debug("Log uploaded: {} → {}", localPath, storagePath);
        } catch (Exception e) {
            log.warn("Failed to upload log to FileStorage: {}", e.getMessage());
        }
    }

    /**
     * 流式读取日志：先查本地，本地没有则从 FileStorage 下载到本地再读。
     * 从 {@code offsetLine} 起收集最多 {@link #MAX_LINES_PER_RESPONSE} 行
     * 或 {@link #MAX_BYTES_PER_RESPONSE} 字节；达到任一上限立即 break。
     */
    public LogResponse readLog(String logPath, int offsetLine) {
        if (logPath == null) {
            return new LogResponse("", 0);
        }
        Path localPath = Paths.get(toLocalPath(logPath));
        if (Files.exists(localPath)) {
            return readLocalLog(localPath, offsetLine);
        }
        try {
            FileStorage storage = fileConfigService.required();
            if (storage.exists(logPath)) {
                Files.createDirectories(localPath.getParent());
                try (InputStream is = storage.download(logPath)) {
                    Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                }
                return readLocalLog(localPath, offsetLine);
            }
        } catch (Exception e) {
            log.warn("Failed to download log from FileStorage: {}", logPath, e);
        }
        return new LogResponse("", 0);
    }

    private LogResponse readLocalLog(Path path, int offsetLine) {
        int from = Math.max(offsetLine, 0);
        StringBuilder buf = new StringBuilder();
        int idx = 0;
        int collected = 0;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (idx >= from) {
                    if (collected >= MAX_LINES_PER_RESPONSE || buf.length() >= MAX_BYTES_PER_RESPONSE) {
                        break;
                    }
                    buf.append(line).append('\n');
                    collected++;
                }
                idx++;
            }
            return new LogResponse(buf.toString(), from + collected);
        } catch (IOException e) {
            log.warn("Failed to read local log: {}", path, e);
            return new LogResponse("Failed to read log: " + e.getMessage(), offsetLine);
        }
    }
}
