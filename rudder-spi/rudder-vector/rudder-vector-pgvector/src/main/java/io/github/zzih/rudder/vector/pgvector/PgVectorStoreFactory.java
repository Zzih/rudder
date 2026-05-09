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

package io.github.zzih.rudder.vector.pgvector;

import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.vector.api.VectorStore;
import io.github.zzih.rudder.vector.api.VectorStoreFactory;

import java.util.List;

import com.google.auto.service.AutoService;

/** PostgreSQL pgvector 扩展工厂。 */
@AutoService(VectorStoreFactory.class)
public class PgVectorStoreFactory implements VectorStoreFactory<PgVectorProperties> {

    public static final String PROVIDER = "PGVECTOR";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<PgVectorProperties> propertiesClass() {
        return PgVectorProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("host").label("Host").type("input")
                        .required(true).placeholder("127.0.0.1")
                        .build(),
                PluginParamDefinition.builder()
                        .name("port").label("Port").type("input")
                        .required(false).placeholder("5432").defaultValue("5432")
                        .build(),
                PluginParamDefinition.builder()
                        .name("database").label("Database").type("input")
                        .required(true).placeholder("rudder")
                        .build(),
                PluginParamDefinition.builder()
                        .name("schema").label("Schema").type("input")
                        .required(false).placeholder("public").defaultValue("public")
                        .build(),
                PluginParamDefinition.builder()
                        .name("username").label("Username").type("input")
                        .required(true)
                        .build(),
                PluginParamDefinition.builder()
                        .name("password").label("Password").type("password")
                        .required(false)
                        .build());
    }

    @Override
    public VectorStore create(ProviderContext ctx, PgVectorProperties props) {
        return new PgVectorStore(props);
    }
}
