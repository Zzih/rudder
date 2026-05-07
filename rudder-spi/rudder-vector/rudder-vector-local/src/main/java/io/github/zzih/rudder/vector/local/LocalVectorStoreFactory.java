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

package io.github.zzih.rudder.vector.local;

import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.vector.api.VectorStore;
import io.github.zzih.rudder.vector.api.VectorStoreFactory;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;

/** 本地向量存储 provider。向量方法全部 no-op,语义搜索返回空(由调用方走 MySQL FULLTEXT 降级)。 */
@AutoService(VectorStoreFactory.class)
public class LocalVectorStoreFactory implements VectorStoreFactory {

    public static final String PROVIDER = "LOCAL";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of();
    }

    @Override
    public VectorStore create(ProviderContext ctx, Map<String, String> config) {
        return new FulltextVectorStore();
    }
}
