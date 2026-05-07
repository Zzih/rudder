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

package io.github.zzih.rudder.mcp.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 防漂移校验：{@link McpTokenStageFlow} 内硬编码的 {@code HIGH_SENSITIVITY_CAPS} 列表
 * 必须与 {@link CapabilityCatalog} 中标注 {@link Capability.Sensitivity#HIGH} 的 capability id 集合一致。
 *
 * <p>原因：{@code McpTokenStageFlow} 在 {@code rudder-service-server}，{@code CapabilityCatalog} 在
 * {@code rudder-mcp}。后者依赖前者（避免循环依赖），所以 stage flow 不能直接引用 catalog —
 * 不得不复制一份 id 列表。本测试在构建期校验两者一致，把"忘改"风险编译期化。
 */
class HighSensitivityConsistencyTest {

    @Test
    @DisplayName("McpTokenStageFlow.HIGH_SENSITIVITY_CAPS == CapabilityCatalog 中所有 HIGH capability id")
    void highSensitivityListsInSync() throws Exception {
        Class<?> stageFlowClass = Class.forName(
                "io.github.zzih.rudder.service.workflow.stage.McpTokenStageFlow");
        @SuppressWarnings("unchecked")
        List<String> stageFlowList = (List<String>) readPrivateStaticField(
                stageFlowClass, "HIGH_SENSITIVITY_CAPS");

        Set<String> catalogHighIds = CapabilityCatalog.ALL.stream()
                .filter(c -> c.sensitivity() == Capability.Sensitivity.HIGH)
                .map(Capability::id)
                .collect(Collectors.toSet());

        assertThat(Set.copyOf(stageFlowList))
                .as("HIGH capability lists drift detected. Update either CapabilityCatalog "
                        + "or McpTokenStageFlow.HIGH_SENSITIVITY_CAPS.")
                .isEqualTo(catalogHighIds);
    }

    private static Object readPrivateStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(null);
    }
}
