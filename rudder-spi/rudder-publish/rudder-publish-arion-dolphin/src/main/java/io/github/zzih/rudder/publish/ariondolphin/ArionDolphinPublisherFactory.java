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

package io.github.zzih.rudder.publish.ariondolphin;

import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.spi.PublisherFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

import io.github.zzih.arion.dolphin.client.ArionClient;

@AutoService(PublisherFactory.class)
public class ArionDolphinPublisherFactory implements PublisherFactory<ArionDolphinProperties> {

    public static final String PROVIDER = "ARION_DOLPHIN";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<ArionDolphinProperties> propertiesClass() {
        return ArionDolphinProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("url").label("Arion Gateway URL").type("input")
                        .required(true).placeholder("http://arion.example.com")
                        .build(),
                PluginParamDefinition.builder()
                        .name("token").label("Arion Token").type("password")
                        .required(false).placeholder("optional bearer token")
                        .build());
    }

    @Override
    public Publisher create(ProviderContext ctx, ArionDolphinProperties props) {
        ArionClient client = new ArionClient(props.url(), props.token());
        return new ArionDolphinPublisher(client);
    }
}
