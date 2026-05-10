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

package io.github.zzih.rudder.publish.rudderdolphin;

import io.github.zzih.rudder.dolphin.client.RudderDolphinClient;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.spi.PublisherFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(PublisherFactory.class)
public class RudderDolphinPublisherFactory implements PublisherFactory<RudderDolphinProperties> {

    public static final String PROVIDER = "RUDDER_DOLPHIN";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public Class<RudderDolphinProperties> propertiesClass() {
        return RudderDolphinProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("url").label("Rudder-Dolphin Gateway URL").type("input")
                        .required(true).placeholder("http://rudder-dolphin.example.com")
                        .build(),
                PluginParamDefinition.builder()
                        .name("token").label("Rudder-Dolphin Token").type("password")
                        .required(false).placeholder("optional bearer token")
                        .build());
    }

    @Override
    public Publisher create(ProviderContext ctx, RudderDolphinProperties props) {
        RudderDolphinClient client = new RudderDolphinClient(props.url(), props.token());
        return new RudderDolphinPublisher(client);
    }
}
