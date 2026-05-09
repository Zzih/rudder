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

package io.github.zzih.rudder.metadata.openmetadata;

import io.github.zzih.rudder.metadata.api.MetadataClient;
import io.github.zzih.rudder.metadata.api.spi.MetadataClientFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(MetadataClientFactory.class)
public class OpenMetadataClientFactory implements MetadataClientFactory<OpenMetadataProperties> {

    @Override
    public String getProvider() {
        return "OPENMETADATA";
    }

    @Override
    public Class<OpenMetadataProperties> propertiesClass() {
        return OpenMetadataProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("url").label("OpenMetadata URL").type("input")
                        .required(true).placeholder("http://localhost:8585")
                        .build(),
                PluginParamDefinition.builder()
                        .name("token").label("JWT Token").type("password")
                        .required(true).placeholder("eyJraWQiOi...")
                        .build());
    }

    @Override
    public MetadataClient create(ProviderContext ctx, OpenMetadataProperties props) {
        return new OpenMetadataClient(props.url(), props.token());
    }
}
