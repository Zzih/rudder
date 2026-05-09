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

package io.github.zzih.rudder.service.workflow;

import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.service.workflow.dto.TaskDefinitionDTO;
import io.github.zzih.rudder.service.workflow.dto.WorkflowDefinitionDTO;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流内容指纹(SHA-256 hex)。GET /detail 返回当前指纹,PUT 时回传作为乐观锁校验。
 *
 * <p>**严格不变量**:GET 算 hash 的字段集 == PUT 校验算 hash 的字段集,两边只走 {@link #compute}。
 * 任何字段集偏移(漏 / 多)会让某些"非真冲突"被误判为冲突,或反之让真冲突漏过。
 *
 * <p>不参与 hash 的字段:metadata 类(id / code / workspaceId / createdAt / updatedAt /
 * publishedVersionId / workflowDefinitionCode)—— 这些不是用户在编辑器里能改的内容。
 */
public final class WorkflowHashUtils {

    private WorkflowHashUtils() {
    }

    public static String compute(WorkflowDefinitionDTO wf, List<TaskDefinitionDTO> taskDefs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", wf.getName());
        root.put("description", wf.getDescription());
        root.put("dagJson", wf.getDagJson());
        root.put("globalParams", wf.getGlobalParams());
        root.put("tasks", taskDefs == null ? List.of()
                : taskDefs.stream()
                        .sorted(Comparator.comparing(TaskDefinitionDTO::getCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(WorkflowHashUtils::taskFingerprint)
                        .toList());
        return CryptoUtils.sha256Hex(JsonUtils.toJson(root));
    }

    private static Map<String, Object> taskFingerprint(TaskDefinitionDTO t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("taskType", t.getTaskType());
        m.put("script", t.getScript());
        m.put("description", t.getDescription());
        m.put("inputParams", t.getInputParams());
        m.put("outputParams", t.getOutputParams());
        m.put("priority", t.getPriority());
        m.put("delayTime", t.getDelayTime());
        m.put("retryTimes", t.getRetryTimes());
        m.put("retryInterval", t.getRetryInterval());
        m.put("timeout", t.getTimeout());
        m.put("timeoutEnabled", t.getTimeoutEnabled());
        m.put("timeoutNotifyStrategy", t.getTimeoutNotifyStrategy());
        m.put("isEnabled", t.getIsEnabled());
        return m;
    }
}
