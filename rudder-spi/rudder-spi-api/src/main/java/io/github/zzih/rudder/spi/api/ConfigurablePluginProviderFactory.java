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
import java.util.Map;

/**
 * 供用户配置驱动的 SPI 工厂共用的公共部分：provider 标识、前端参数声明、接入指南、
 * 字段校验与测试钩子。
 *
 * <p>{@code create(...)} 签名因 SPI 而异（不同返回类型 / 不同 context），故不在此接口声明，
 * 由具体 SPI 工厂各自定义。
 *
 * @param <C> provider context 类型（如 {@code ProviderContext} / 特化 context）
 */
public interface ConfigurablePluginProviderFactory<C> extends PluginProviderFactory {

    /** Provider 唯一标识（例如 LOCAL / HDFS / CLAUDE），大小写不敏感。 */
    String getProvider();

    /**
     * SPI family(llm / embedding / vector / metadata / approval / notification / file / runtime / version)。
     * <p>用于 md guide 文件名前缀,避免跨 SPI 的 provider 同名冲突(LLM OPENAI vs Embedding OPENAI,多个 SPI 的 LOCAL)。
     * <p>每类 SPI 的顶级接口(如 {@code LlmClientFactory} / {@code VectorStoreFactory})在自己的 default
     * 实现里 override 返回固定字符串;具体 provider 工厂不用再填。
     */
    String family();

    /** 声明该 provider 需要的配置参数，前端据此动态渲染表单。 */
    List<PluginParamDefinition> params();

    /**
     * 返回 provider 的接入指南（Markdown），前端在配置页内嵌展示。
     * <p>默认从 classpath 的 {@code spi-guide/<family>-<provider_lower>.<lang>.md} 加载,
     * 请求 locale miss 退到 zh。Factory 无需 override,只需在 resources 对应路径放 md 文件。
     */
    default String guide(Locale locale) {
        return SpiGuideLoader.load(family(), getProvider(), locale).body();
    }

    /**
     * Provider 元数据。
     * <p>默认从 md 文件的 YAML front-matter 读 {@code description}。Factory 只需在 md 头写 {@code description:}。
     */
    default ProviderMetadata metadata(Locale locale) {
        String desc = SpiGuideLoader.load(family(), getProvider(), locale).description();
        return desc.isEmpty() ? ProviderMetadata.empty() : ProviderMetadata.of(desc);
    }

    /** 保存配置前做字段级校验。默认 OK。 */
    default ValidationResult validate(Map<String, String> config) {
        return ValidationResult.ok();
    }

    /** 实际测试 provider 是否可用（连 S3 / 认证 Gitea 等）。默认 NOT_SUPPORTED。 */
    default TestResult testConnection(C ctx, Map<String, String> config) {
        return TestResult.notSupported();
    }
}
