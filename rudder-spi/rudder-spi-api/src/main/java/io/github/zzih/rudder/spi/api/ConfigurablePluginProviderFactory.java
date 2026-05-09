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

package io.github.zzih.rudder.spi.api;

import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.spi.api.model.ProviderMetadata;
import io.github.zzih.rudder.spi.api.model.TestResult;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.util.List;
import java.util.Locale;

/**
 * 用户配置驱动的 SPI 工厂公共契约:provider 标识、前端参数声明、强类型 properties POJO、
 * 接入指南、字段校验与连接测试钩子。
 *
 * <p><b>强类型 properties 模型</b>:每个 provider 声明自己的 Properties POJO/record(类型 P),
 * provider_params 列存的就是该 Properties 对象的 JSON 序列化。框架(
 * {@link AbstractConfigurablePluginRegistry})统一做反序列化派发,
 * provider 实现的 {@code create / validate / testConnection} 拿到的都是强类型 P,
 * 不再有 {@code config.get("apiKey")} 这种字符串胶水。
 *
 * <p>{@code create(...)} 因 SPI 而异(不同返回类型 / 不同 context),不在此声明,
 * 由具体 SPI 工厂(如 {@code VectorStoreFactory<P>})自行定义。
 *
 * @param <C> provider context 类型
 * @param <P> provider 配置 POJO 类型
 */
public interface ConfigurablePluginProviderFactory<C, P> extends PluginProviderFactory {

    /** Provider 唯一标识(LOCAL / HDFS / CLAUDE 等),大小写不敏感。 */
    String getProvider();

    /**
     * SPI 类型(llm / embedding / vector / metadata / approval / notification / file / runtime / version 等)。
     * 用作 md guide 文件名前缀,避免跨 SPI 同名 provider 冲突。
     * 顶级 SPI 接口(如 {@code VectorStoreFactory})提供 default,具体 provider 不用填。
     */
    String type();

    /** Properties 类,框架据此把 admin 表单 / DB JSON 反序列化成 P。 */
    Class<P> propertiesClass();

    /** 声明该 provider 需要的配置参数,前端据此动态渲染表单。 */
    List<PluginParamDefinition> params();

    /**
     * Provider 接入指南(Markdown),前端在配置页内嵌展示。
     * 默认从 classpath 的 {@code spi-guide/<type>-<provider_lower>.<lang>.md} 加载。
     */
    default String guide(Locale locale) {
        return SpiGuideLoader.load(type(), getProvider(), locale).body();
    }

    /** Provider 元数据(从 md front-matter 读 description)。 */
    default ProviderMetadata metadata(Locale locale) {
        String desc = SpiGuideLoader.load(type(), getProvider(), locale).description();
        return desc.isEmpty() ? ProviderMetadata.empty() : ProviderMetadata.of(desc);
    }

    /** 保存配置前做强类型字段级校验。默认 OK。 */
    default ValidationResult validate(P props) {
        return ValidationResult.ok();
    }

    /** 实际测试 provider 连通性(连 S3 / 认证 Lark 等)。默认 NOT_SUPPORTED。 */
    default TestResult testConnection(C ctx, P props) {
        return TestResult.notSupported();
    }
}
