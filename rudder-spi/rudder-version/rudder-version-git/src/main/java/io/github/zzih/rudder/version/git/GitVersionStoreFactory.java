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

package io.github.zzih.rudder.version.git;

import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.VersionStoreFactory;
import io.github.zzih.rudder.version.git.client.GiteaClient;

import java.util.List;

import com.google.auto.service.AutoService;

import lombok.extern.slf4j.Slf4j;

/** Git 模式的 VersionStore 工厂。 */
@Slf4j
@AutoService(VersionStoreFactory.class)
public class GitVersionStoreFactory implements VersionStoreFactory<GiteaProperties> {

    public static final String PROVIDER = "GIT";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<GiteaProperties> propertiesClass() {
        return GiteaProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("url").label("Gitea URL").type("input")
                        .required(true).placeholder("http://gitea.example.com:3000")
                        .build(),
                PluginParamDefinition.builder()
                        .name("token").label("API Token").type("password")
                        .required(true).placeholder("spi.version.git.token.placeholder")
                        .build());
    }

    @Override
    public VersionStore create(ProviderContext ctx, GiteaProperties props) {
        GiteaClient client = new GiteaClient(props, ctx.objectMapper());
        return new GitVersionStore(client);
    }
}
