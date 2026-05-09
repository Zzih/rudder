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

package io.github.zzih.rudder.metadata.datahub;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.spi.MetadataClientFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(MetadataClientFactory.class)
public class DatahubMetadataClientFactory implements MetadataClientFactory<DatahubMetadataProperties> {

    @Override
    public String getProvider() {
        return "DATAHUB";
    }

    @Override
    public Class<DatahubMetadataProperties> propertiesClass() {
        return DatahubMetadataProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("url").label("DataHub GMS URL").type("input")
                        .required(true).placeholder("http://localhost:8080")
                        .build(),
                PluginParamDefinition.builder()
                        .name("token").label("Access Token").type("password")
                        .required(true).placeholder("eyJhbGciOiJIUzI1NiJ9...")
                        .build(),
                PluginParamDefinition.builder()
                        .name("environment").label("Environment").type("input")
                        .required(false).placeholder("PROD")
                        .defaultValue("PROD")
                        .build());
    }

    @Override
    public MetadataClient create(ProviderContext ctx, DatahubMetadataProperties props) {
        return new DatahubMetadataClient(props.url(), props.token(), props.environment());
    }
}
