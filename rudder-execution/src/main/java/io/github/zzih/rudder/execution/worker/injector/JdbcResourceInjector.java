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

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.datasource.service.DatasourceService;
import io.github.zzih.rudder.task.api.task.DataSourceAwareTask;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 向声明了 {@link DataSourceAwareTask} 的任务注入 DataSourceInfo。
 * 同时覆盖 JdbcTask（MySQL、Hive 等）和集群 SQL 任务（FlinkSQL、SparkSQL）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcResourceInjector implements ResourceInjector<DataSourceAwareTask> {

    private final DatasourceService datasourceService;

    @Override
    public Class<DataSourceAwareTask> taskType() {
        return DataSourceAwareTask.class;
    }

    @Override
    public void inject(DataSourceAwareTask task, InjectionContext ctx) {
        Long datasourceId = extractDatasourceId(ctx.getInstance());
        if (datasourceId == null) {
            log.warn("No datasource ID for JdbcTask, skipping injection");
            return;
        }

        task.setDataSourceInfo(datasourceService.getDataSourceInfo(datasourceId));
        log.trace("Injected DataSourceInfo for datasource id={}", datasourceId);
    }

    private Long extractDatasourceId(TaskInstance instance) {
        String content = instance.getContent();
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            var parsed = JsonUtils.fromJson(
                    content, new TypeReference<Map<String, Object>>() {
                    });
            Object val = parsed.get("dataSourceId");
            return val != null ? Long.valueOf(val.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
