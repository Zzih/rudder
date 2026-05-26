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

package io.github.zzih.rudder.result.api.spi;

import io.github.zzih.rudder.result.api.ResultFormat;
import io.github.zzih.rudder.result.api.ResultProperties;
import io.github.zzih.rudder.spi.api.ConfigurablePluginProviderFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

/**
 * Result format provider 工厂。所有 5 个 provider(PARQUET/CSV/JSON/ORC/AVRO)共用 {@link ResultProperties},
 * 在 -api 模块统一定义,format 选型不影响参数 schema。
 *
 * <p>实现需在 {@code META-INF/services/io.github.zzih.rudder.result.api.spi.ResultFormatFactory}
 * 中登记,由 {@code ResultPluginManager} 通过 {@link java.util.ServiceLoader} 发现。必须提供无参构造函数。
 */
public interface ResultFormatFactory extends ConfigurablePluginProviderFactory<ProviderContext, ResultProperties> {

    @Override
    default String type() {
        return "result";
    }

    @Override
    default Class<ResultProperties> propertiesClass() {
        return ResultProperties.class;
    }

    /** 5 个 provider 共用 {@link ResultProperties} 一份 schema,接口层 default 暴露,实现不重复 override。 */
    @Override
    default List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("defaultQueryRows")
                        .label("spi.result.defaultQueryRows.label")
                        .type("number")
                        .required(false)
                        .placeholder("spi.result.defaultQueryRows.placeholder")
                        .defaultValue(String.valueOf(ResultProperties.DEFAULT_QUERY_ROWS))
                        .min(1)
                        .max(10_000_000)
                        .step(1000)
                        .build());
    }

    ResultFormat create(ProviderContext ctx, ResultProperties props);
}
