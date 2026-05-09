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

package io.github.zzih.rudder.vector.qdrant;

import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.vector.api.VectorStore;
import io.github.zzih.rudder.vector.api.VectorStoreFactory;

import java.util.List;

import com.google.auto.service.AutoService;

/** Qdrant gRPC 客户端工厂。 */
@AutoService(VectorStoreFactory.class)
public class QdrantVectorStoreFactory implements VectorStoreFactory<QdrantProperties> {

    public static final String PROVIDER = "QDRANT";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<QdrantProperties> propertiesClass() {
        return QdrantProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("host").label("Host").type("input")
                        .required(true).placeholder("127.0.0.1")
                        .build(),
                PluginParamDefinition.builder()
                        .name("port").label("gRPC Port").type("input")
                        .required(false).placeholder("6334").defaultValue("6334")
                        .build(),
                PluginParamDefinition.builder()
                        .name("useTls").label("Use TLS").type("boolean")
                        .required(false).defaultValue("false")
                        .build(),
                PluginParamDefinition.builder()
                        .name("apiKey").label("API Key").type("password")
                        .required(false)
                        .build());
    }

    @Override
    public VectorStore create(ProviderContext ctx, QdrantProperties props) {
        return new QdrantVectorStore(props.getHost(), props.getPort(), props.isUseTls(), props.getApiKey());
    }
}
