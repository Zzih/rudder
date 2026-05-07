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

package io.github.zzih.rudder.execution.rpc;

import io.github.zzih.rudder.common.execution.ResultResponse;
import io.github.zzih.rudder.common.utils.crypto.PathSecurityUtils;
import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.local.LocalFileStorage;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultPage;
import io.github.zzih.rudder.result.api.plugin.ResultPluginManager;
import io.github.zzih.rudder.rpc.service.IResultService;
import io.github.zzih.rudder.service.config.FileConfigService;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 结果读取服务。
 * <p>
 * 和日志一致：先查本地 {@code local-dir}，本地没有则从 FileStorage 下载到本地再读。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultServiceImpl implements IResultService {

    private final FileConfigService fileConfigService;
    private final ResultPluginManager resultPluginManager;

    @Value("${rudder.file.local-dir:./data}")
    private String localDir;

    private Path baseDir;

    @PostConstruct
    void init() {
        this.baseDir = Paths.get(localDir).toAbsolutePath().normalize();
    }

    @Override
    public ResultResponse fetchResult(String resultPath, int offset, int limit) {
        if (resultPath == null) {
            return new ResultResponse(List.of(), List.of(), 0, offset, limit);
        }
        Path localFile;
        try {
            localFile = PathSecurityUtils.resolveWithinBase(baseDir, resultPath);
        } catch (SecurityException e) {
            log.warn("Rejecting result path traversal attempt: {}", resultPath);
            return new ResultResponse(List.of(), List.of(), 0, offset, limit);
        }
        try {
            ResultFormat format = resultPluginManager.getByExtension(resultPath);
            FileStorage storage = fileConfigService.required();
            if (!Files.exists(localFile)) {
                if (storage.exists(resultPath)) {
                    Files.createDirectories(localFile.getParent());
                    try (InputStream is = storage.download(resultPath)) {
                        Files.copy(is, localFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    log.debug("Result downloaded: {} → {}", resultPath, localFile);
                }
                // 按 format 自己声明的伴生文件后缀拉到本地,format.read 内部用啥(idx/footer/...)对此处透明
                for (String suffix : format.sidecarSuffixes()) {
                    String remote = resultPath + suffix;
                    Path local = Paths.get(localFile.toString() + suffix);
                    if (!Files.exists(local) && storage.exists(remote)) {
                        try (InputStream is = storage.download(remote)) {
                            Files.copy(is, local, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            LocalFileStorage localStorage = new LocalFileStorage(localDir);
            ResultPage page = format.read(localStorage, resultPath, offset, limit);
            return new ResultResponse(page.getColumns(), page.getRows(), page.getTotalRows(), offset, limit);
        } catch (Exception e) {
            log.warn("Failed to read result file: {}", resultPath, e);
            return new ResultResponse(List.of(), List.of(), 0, offset, limit);
        }
    }
}
