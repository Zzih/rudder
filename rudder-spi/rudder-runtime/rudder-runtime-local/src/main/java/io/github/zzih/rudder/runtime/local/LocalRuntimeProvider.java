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

import io.github.zzih.rudder.common.utils.runtime.RuntimeConfigUtils;
import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.spi.EngineRuntimeProvider;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(EngineRuntimeProvider.class)
public class LocalRuntimeProvider implements EngineRuntimeProvider<LocalRuntimeFormProperties> {

    private static final String ENV_VARS_PLACEHOLDER =
            "# 一行一个 KEY=VALUE,空行和 # 开头的注释行会被忽略。\n"
                    + "# 例如:\n"
                    + "# JAVA_HOME=/opt/jdk-21\n"
                    + "# SPARK_HOME=/opt/bigdata/spark\n"
                    + "# HADOOP_CONF_DIR=/etc/hadoop/conf\n"
                    + "# PATH=$SPARK_HOME/bin:$PATH";

    @Override
    public String getProvider() {
        return LocalRuntime.PROVIDER_KEY;
    }

    @Override
    public Class<LocalRuntimeFormProperties> propertiesClass() {
        return LocalRuntimeFormProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("envVars").label("spi.runtime.envVars.label").type("textarea")
                        .required(false).placeholder(ENV_VARS_PLACEHOLDER)
                        .build());
    }

    @Override
    public EngineRuntime create(ProviderContext ctx, LocalRuntimeFormProperties form) {
        return new LocalRuntime(RuntimeConfigUtils.parseProperties(form.envVars()));
    }
}
