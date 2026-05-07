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

package io.github.zzih.rudder.ai.tool.builtin;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.TaskInstanceService;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * 以 IDE 模式触发脚本执行,返回 taskInstanceId。
 * <p>
 * 执行是异步的 —— 调用方拿到 id 之后,用 get_execution_logs / get_execution_result 轮询状态。
 */
@Component
@RequiredArgsConstructor
public class ExecuteScriptTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "code":{"type":"integer","description":"Script code to execute"},
                        "datasourceId":{"type":"integer","description":"Override datasource (optional)"},
                        "overrideSql":{"type":"string","description":"Override SQL body for this run only (optional). Does not persist to the script."},
                        "executionMode":{"type":"string","enum":["BATCH","STREAMING"]}
                      },
                      "required":["code"]
                    }""");

    private final TaskInstanceService taskInstanceService;

    @Override
    public String name() {
        return "execute_script";
    }

    @Override
    public String description() {
        return "Dispatch a script for execution in IDE mode. Returns the taskInstance id; "
                + "use get_execution_logs / get_execution_result to poll. Async — does not block.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        long code = input.get("code").asLong();
        Long datasourceId = input.hasNonNull("datasourceId") ? input.get("datasourceId").asLong() : null;
        String overrideSql = input.hasNonNull("overrideSql") ? input.get("overrideSql").asText() : null;
        String executionMode = input.hasNonNull("executionMode") ? input.get("executionMode").asText() : null;

        TaskInstance instance = taskInstanceService.execute(code, datasourceId, overrideSql, executionMode);

        ObjectNode out = JsonUtils.createObjectNode();
        out.put("taskInstanceId", instance.getId());
        out.put("status", instance.getStatus() == null ? "PENDING" : instance.getStatus().name());
        out.put("executionHost", instance.getExecutionHost());
        return JsonUtils.toJson(out);
    }
}
