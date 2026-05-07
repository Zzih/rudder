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

package io.github.zzih.rudder.ai.knowledge;

import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 脚本 TaskType 到可见元数据 engineType 的映射。
 * 用于在 RAG 检索 SCHEMA 类文档时做硬过滤,防止跨引擎元数据幻觉。
 * <p>
 * 默认规则:
 * <ul>
 *   <li>HIVE_SQL → [HIVE]</li>
 *   <li>STARROCKS_SQL → [STARROCKS, HIVE](hive_catalog 接入)</li>
 *   <li>TRINO_SQL → [TRINO, HIVE]</li>
 *   <li>SPARK_SQL → [SPARK, HIVE]</li>
 *   <li>FLINK_SQL → [FLINK, HIVE]</li>
 *   <li>MYSQL → [MYSQL]</li>
 *   <li>PYTHON / SHELL / SEATUNNEL / JAR → null(不限制,允许跨引擎)</li>
 * </ul>
 * 可通过 application.yml 的 rudder.ai.engine-visibility 覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rudder.ai.engine-visibility")
public class EngineCompatibility {

    /** key: TaskType name;value: 允许召回的 engineType 列表。空列表=允许全部(不过滤)。 */
    private Map<String, List<String>> rules = defaultRules();

    private static Map<String, List<String>> defaultRules() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("HIVE_SQL", List.of("HIVE"));
        m.put("STARROCKS_SQL", List.of("STARROCKS", "HIVE"));
        m.put("TRINO_SQL", List.of("TRINO", "HIVE"));
        m.put("SPARK_SQL", List.of("SPARK", "HIVE"));
        m.put("FLINK_SQL", List.of("FLINK", "HIVE"));
        m.put("MYSQL", List.of("MYSQL"));
        return m;
    }

    /** 返回当前 TaskType 允许看到的 engineType;null 表示不过滤(允许全部)。 */
    public List<String> allowedEngines(TaskType taskType) {
        if (taskType == null) {
            return null;
        }
        return allowedEngines(taskType.name());
    }

    public List<String> allowedEngines(String taskTypeName) {
        if (taskTypeName == null) {
            return null;
        }
        List<String> ret = rules.get(taskTypeName.toUpperCase(Locale.ROOT));
        return (ret == null || ret.isEmpty()) ? null : ret;
    }

    /** 给 system prompt 用的提示文案。 */
    public String systemPromptHint(TaskType taskType, Map<String, String> engineAccessTemplates) {
        List<String> allowed = allowedEngines(taskType);
        if (allowed == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Current script engine: ").append(taskType.name())
                .append(". You may reference metadata ONLY from engines: ")
                .append(String.join(", ", allowed)).append(".\n");
        sb.append("Do NOT reference tables from other engines even if they appear in context.\n");
        if (engineAccessTemplates != null && !engineAccessTemplates.isEmpty()) {
            sb.append("For cross-engine access, use the Access Paths given in each table doc.\n");
        }
        return sb.toString();
    }
}
