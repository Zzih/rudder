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

package io.github.zzih.rudder.runtime.aws;

import io.github.zzih.rudder.common.utils.runtime.RuntimeConfigUtils;
import io.github.zzih.rudder.runtime.api.EngineRuntime;
import io.github.zzih.rudder.runtime.api.spi.EngineRuntimeProvider;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.emrserverless.EmrServerlessClient;
import software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2Client;

@Slf4j
@AutoService(EngineRuntimeProvider.class)
public class AwsRuntimeProvider implements EngineRuntimeProvider {

    private volatile EmrServerlessClient currentEmrClient;
    private volatile KinesisAnalyticsV2Client currentFlinkClient;

    @Override
    public String getProvider() {
        return AwsRuntime.PROVIDER_KEY;
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
    public EngineRuntime create(ProviderContext ctx, Map<String, String> config) {
        Map<String, String> parsed = RuntimeConfigUtils.parseProperties(config.get("config"));
        AwsRuntimeProperties props = buildProperties(parsed);
        Map<String, String> envVars = RuntimeConfigUtils.parseProperties(config.get("envVars"));
        Region region = Region.of(props.getRegion());

        EmrServerlessClient emrClient = EmrServerlessClient.builder().region(region).build();
        KinesisAnalyticsV2Client flinkClient = KinesisAnalyticsV2Client.builder().region(region).build();

        currentEmrClient = emrClient;
        currentFlinkClient = flinkClient;

        return new AwsRuntime(props, envVars, emrClient, flinkClient);
    }

    @Override
    public void closeResources() {
        closeQuietly(currentEmrClient);
        closeQuietly(currentFlinkClient);
        currentEmrClient = null;
        currentFlinkClient = null;
    }

    private void closeQuietly(AutoCloseable client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Failed to close AWS SDK client: {}", e.getMessage());
        }
    }

    private AwsRuntimeProperties buildProperties(Map<String, String> config) {
        AwsRuntimeProperties props = new AwsRuntimeProperties();
        applyIfPresent(config, "region", props::setRegion);
        props.getSpark().setApplicationId(config.get("spark.applicationId"));
        props.getSpark().setExecutionRoleArn(config.get("spark.executionRoleArn"));
        props.getFlink().setServiceExecutionRole(config.get("flink.serviceExecutionRole"));
        props.getFlink().setS3Bucket(config.get("flink.s3Bucket"));
        applyIfPresent(config, "flink.runtimeEnvironment", props.getFlink()::setRuntimeEnvironment);
        return props;
    }

    private static void applyIfPresent(Map<String, String> config, String key,
                                       java.util.function.Consumer<String> setter) {
        String value = config.get(key);
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

}
