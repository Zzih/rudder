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
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.llm.api.tool.ToolExecutionContext;
import io.github.zzih.rudder.service.script.ScriptService;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/** 在指定文件夹下创建脚本。写类工具,只读模式会被 PermissionGate 拒。 */
@Component
@RequiredArgsConstructor
public class CreateScriptTool extends AbstractBuiltinTool {

    private static final JsonNode SCHEMA = JsonUtils.parseTree(
            """
                    {
                      "type":"object",
                      "properties":{
                        "dirId":{"type":"integer","description":"Target folder id; use list_folders to discover"},
                        "name":{"type":"string"},
                        "taskType":{"type":"string","description":"One of STARROCKS_SQL, TRINO_SQL, SPARK_SQL, HIVE_SQL, MYSQL, FLINK_SQL, PYTHON, SHELL, SEATUNNEL"},
                        "content":{"type":"string","description":"Raw script body (SQL / python / conf)"}
                      },
                      "required":["dirId","name","taskType","content"]
                    }""");

    private final ScriptService scriptService;

    @Override
    public String name() {
        return "create_script";
    }

    @Override
    public String description() {
        return "Create a new script under a folder. Use list_folders first to pick dirId. "
                + "Provide raw SQL / Python / Shell / SeaTunnel body as `content`.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode input, ToolExecutionContext ctx) {
        Long dirId = input.get("dirId").asLong();
        String scriptName = input.get("name").asText();
        String taskTypeRaw = input.get("taskType").asText();
        String content = input.get("content").asText();

        TaskType taskType;
        try {
            taskType = TaskType.valueOf(taskTypeRaw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown taskType: " + taskTypeRaw);
        }

        Script script = new Script();
        script.setWorkspaceId(ctx.getWorkspaceId());
        script.setDirId(dirId);
        script.setName(scriptName);
        script.setTaskType(taskType);
        script.setContent(content);
        script.setSourceType(SourceType.IDE);
        Script created = scriptService.create(script);

        ObjectNode out = JsonUtils.createObjectNode();
        out.put("code", created.getCode());
        out.put("name", created.getName());
        out.put("taskType", created.getTaskType().name());
        out.put("dirId", created.getDirId() == null ? 0L : created.getDirId());
        return JsonUtils.toJson(out);
    }
}
