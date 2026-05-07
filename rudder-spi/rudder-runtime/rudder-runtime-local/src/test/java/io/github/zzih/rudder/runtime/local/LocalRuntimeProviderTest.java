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

package io.github.zzih.rudder.runtime.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LocalRuntimeProviderTest {

    private final LocalRuntimeProvider provider = new LocalRuntimeProvider();

    @Test
    void provider_key_matches_constant() {
        assertEquals(LocalRuntime.PROVIDER_KEY, provider.getProvider());
    }

    @Test
    void params_exposes_envVars_textarea() {
        List<PluginParamDefinition> params = provider.params();
        assertEquals(1, params.size());
        PluginParamDefinition p = params.get(0);
        assertEquals("envVars", p.getName());
        assertEquals("textarea", p.getType());
        assertFalse(p.isRequired());
    }

    @Test
    void create_with_blank_envVars_yields_empty_map() {
        EngineRuntime r = provider.create(null, Map.of());
        assertEquals(Map.of(), r.envVars());
        assertEquals(LocalRuntime.PROVIDER_KEY, r.provider());
    }

    @Test
    void create_with_envVars_parses_KV_lines() {
        Map<String, String> config = new HashMap<>();
        config.put("envVars", """
                # comment line
                JAVA_HOME=/opt/jdk-21
                SPARK_HOME=/opt/bigdata/spark

                HADOOP_CONF_DIR=/etc/hadoop/conf
                """);
        EngineRuntime r = provider.create(null, config);
        Map<String, String> env = r.envVars();
        assertEquals("/opt/jdk-21", env.get("JAVA_HOME"));
        assertEquals("/opt/bigdata/spark", env.get("SPARK_HOME"));
        assertEquals("/etc/hadoop/conf", env.get("HADOOP_CONF_DIR"));
        assertEquals(3, env.size());
    }

    @Test
    void localRuntime_does_not_takeover_any_taskType() {
        EngineRuntime r = provider.create(null, Map.of());
        for (TaskType t : TaskType.values()) {
            assertTrue(r.taskFactoryFor(t).isEmpty(),
                    "Local 不应接管任何 TaskType,但接管了: " + t);
        }
    }

    @Test
    void localRuntime_healthCheck_is_healthy() {
        EngineRuntime r = provider.create(null, Map.of());
        assertEquals(HealthStatus.healthy(), r.healthCheck());
    }
}
