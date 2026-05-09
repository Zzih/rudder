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

package io.github.zzih.rudder.version.local;

import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.NoProps;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.VersionStoreFactory;

import java.util.List;

import com.google.auto.service.AutoService;

/** 本地内置版本存储工厂:storageRef 透传内容本身,平台 DB 同一张表存储,无额外配置参数。 */
@AutoService(VersionStoreFactory.class)
public class LocalVersionStoreFactory implements VersionStoreFactory<NoProps> {

    public static final String PROVIDER = "LOCAL";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<NoProps> propertiesClass() {
        return NoProps.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of();
    }

    @Override
    public VersionStore create(ProviderContext ctx, NoProps props) {
        return new LocalVersionStore();
    }
}
