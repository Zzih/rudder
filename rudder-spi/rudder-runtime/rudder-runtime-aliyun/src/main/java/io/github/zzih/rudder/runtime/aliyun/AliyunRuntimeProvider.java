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

package io.github.zzih.rudder.runtime.aliyun;

import io.github.zzih.rudder.common.utils.runtime.RuntimeConfigUtils;
import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.spi.EngineRuntimeProvider;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.aliyun.teaopenapi.models.Config;
import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

/** 阿里云 Runtime provider(Serverless Spark + VVP Flink)。 */
@Slf4j
@AutoService(EngineRuntimeProvider.class)
public class AliyunRuntimeProvider implements EngineRuntimeProvider<AliyunRuntimeFormProperties> {

    @Override
    public String getProvider() {
        return AliyunRuntime.PROVIDER_KEY;
    }

    @Override
    public Class<AliyunRuntimeFormProperties> propertiesClass() {
        return AliyunRuntimeFormProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("config").label("spi.runtime.config.label").type("textarea")
                        .required(true)
                        .placeholder("spi.runtime.config.placeholder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("envVars").label("spi.runtime.envVars.label").type("textarea")
                        .required(false)
                        .placeholder("spi.runtime.envVars.placeholder")
                        .build());
    }

    @Override
    public EngineRuntime create(ProviderContext ctx, AliyunRuntimeFormProperties form) {
        Map<String, String> parsed = RuntimeConfigUtils.parseProperties(form.config());
        AliyunRuntimeProperties props = buildProperties(parsed);
        validate(props);
        Map<String, String> envVars = RuntimeConfigUtils.parseProperties(form.envVars());

        com.aliyun.ververica20220718.Client vvpClient = newVvpClient(props);
        com.aliyun.emr_serverless_spark20230808.Client sparkClient = newSparkClient(props);
        return new AliyunRuntime(props, envVars, vvpClient, sparkClient);
    }

    private AliyunRuntimeProperties buildProperties(Map<String, String> config) {
        AliyunRuntimeProperties props = new AliyunRuntimeProperties();
        props.setAccessKeyId(config.get("accessKeyId"));
        props.setAccessKeySecret(config.get("accessKeySecret"));
        applyIfPresent(config, "regionId", props::setRegionId);
        props.getSpark().setWorkspaceId(config.get("spark.workspaceId"));
        props.getSpark().setResourceQueueId(config.get("spark.resourceQueueId"));
        props.getFlink().setWorkspaceId(config.get("flink.workspaceId"));
        applyIfPresent(config, "flink.namespace", props.getFlink()::setNamespace);
        return props;
    }

    private void validate(AliyunRuntimeProperties props) {
        if (isBlank(props.getAccessKeyId()) || isBlank(props.getAccessKeySecret())) {
            throw new IllegalArgumentException("Aliyun runtime: accessKeyId/accessKeySecret are required");
        }
    }

    private static void applyIfPresent(Map<String, String> config, String key,
                                       java.util.function.Consumer<String> setter) {
        String value = config.get(key);
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private com.aliyun.ververica20220718.Client newVvpClient(AliyunRuntimeProperties props) {
        try {
            Config cfg = new Config()
                    .setAccessKeyId(props.getAccessKeyId())
                    .setAccessKeySecret(props.getAccessKeySecret());
            cfg.endpoint = props.flinkEndpoint();
            return new com.aliyun.ververica20220718.Client(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Aliyun VVP client: " + e.getMessage(), e);
        }
    }

    private com.aliyun.emr_serverless_spark20230808.Client newSparkClient(AliyunRuntimeProperties props) {
        try {
            Config cfg = new Config()
                    .setAccessKeyId(props.getAccessKeyId())
                    .setAccessKeySecret(props.getAccessKeySecret());
            cfg.endpoint = props.sparkEndpoint();
            return new com.aliyun.emr_serverless_spark20230808.Client(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Aliyun Spark client: " + e.getMessage(), e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
