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

package io.github.zzih.rudder.execution.worker.injector;

import io.github.zzih.rudder.common.utils.io.StoragePathUtils;
import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.service.config.FileConfigService;
import io.github.zzih.rudder.service.config.ResultConfigService;
import io.github.zzih.rudder.service.redaction.RedactionService;
import io.github.zzih.rudder.service.sink.StreamingFileResultSink;
import io.github.zzih.rudder.task.api.task.ResultableTask;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 给 {@link ResultableTask} 注入 {@link StreamingFileResultSink}。Task 在 handle() 时把 sink 喂给
 * SqlExecutor / Runtime,数据"读 → 脱敏 → 写本地 → 上传"全部在 Task 内部完成,出口元信息
 * (resultPath / rowCount / firstRow) 由 sink 暴露给 Collector / Worker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultSinkInjector implements ResourceInjector<ResultableTask> {

    private final RedactionService redactionService;
    private final FileConfigService fileConfigService;
    private final ResultConfigService resultConfigService;

    @Value("${rudder.file.local-dir:./data}")
    private String localDir;

    @Override
    public Class<ResultableTask> taskType() {
        return ResultableTask.class;
    }

    @Override
    public void inject(ResultableTask task, InjectionContext ctx) {
        String logPath = ctx.getInstance().getLogPath();
        if (logPath == null) {
            log.warn("No logPath in instance, skipping ResultSink injection (task uses default sink)");
            return;
        }
        ResultFormat format = resultConfigService.required();
        String storagePath = StoragePathUtils.resultPathFromLogPath(logPath, format.extension());
        StreamingFileResultSink sink = new StreamingFileResultSink(
                redactionService, fileConfigService, format, localDir, storagePath);
        task.setResultSink(sink);
        log.trace("Injected StreamingFileResultSink for task {}", task.getClass().getSimpleName());
    }
}
