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

package io.github.zzih.rudder.execution.worker;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.service.config.FileConfigService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolve and download task resource files from FileStorage to local working directory.
 * Similar to DolphinScheduler's TaskExecutionContextUtils.downloadResourcesIfNeeded().
 */
@Slf4j
@Component
public class ResourceResolver {

    private final FileConfigService fileConfigService;

    @Value("${rudder.execution.work-dir:/tmp/rudder/tasks}")
    private String workDir;

    @Value("${rudder.execution.clean-work-dir:true}")
    private boolean cleanWorkDir;

    public ResourceResolver(FileConfigService fileConfigService) {
        this.fileConfigService = fileConfigService;
    }

    /**
     * Create task working directory and download resource files.
     * Directory structure: {workDir}/{workflowInstanceId}/{taskInstanceId} or {workDir}/{taskInstanceId}
     *
     * @param workflowInstanceId workflow instance ID (nullable for IDE-triggered tasks)
     * @param taskInstanceId     task instance ID
     * @param filePaths          map of param key to storage-relative path, e.g. {"jarPath": "/jar/app.jar"}
     * @return resolved map of param key to local absolute path
     */
    public ResolveResult resolveResources(Long workflowInstanceId, Long taskInstanceId, Map<String, String> filePaths) {
        Path taskDir = workflowInstanceId != null
                ? Paths.get(workDir, String.valueOf(workflowInstanceId), String.valueOf(taskInstanceId))
                : Paths.get(workDir, String.valueOf(taskInstanceId));
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create task working directory: " + taskDir, e);
        }

        FileStorage storage = fileConfigService.required();
        Map<String, String> resolvedPaths = new HashMap<>();

        for (var entry : filePaths.entrySet()) {
            String key = entry.getKey();
            String storagePath = entry.getValue();

            String fileName = Paths.get(storagePath).getFileName().toString();
            Path localPath = taskDir.resolve(fileName);

            try (InputStream is = storage.download(storagePath)) {
                Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Downloaded resource: {} -> {}", storagePath, localPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to download resource: " + storagePath, e);
            }

            resolvedPaths.put(key, localPath.toAbsolutePath().toString());
        }

        return new ResolveResult(taskDir.toAbsolutePath().toString(), resolvedPaths);
    }

    /**
     * Clean up task working directory after execution.
     */
    public void cleanup(String executePath) {
        if (!cleanWorkDir || executePath == null || executePath.isBlank()) {
            return;
        }
        Path dir = Paths.get(executePath);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
            log.info("Cleaned up task working directory: {}", executePath);
        } catch (IOException e) {
            log.warn("Failed to cleanup working directory: {}", executePath, e);
        }
    }

    public record ResolveResult(String executePath, Map<String, String> resolvedFilePaths) {
    }
}
