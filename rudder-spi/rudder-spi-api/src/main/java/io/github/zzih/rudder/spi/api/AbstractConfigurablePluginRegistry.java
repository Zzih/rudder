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

import io.github.zzih.rudder.common.enums.error.SpiErrorCode;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.spi.api.model.PluginProviderDefinition;
import io.github.zzih.rudder.spi.api.model.ProviderMetadata;
import io.github.zzih.rudder.spi.api.model.TestResult;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 面向"用户可配置 SPI"的注册表基类。
 *
 * <p>收拢 6 个 Manager（File / Ai / Notification / Approval / Metadata / Version / Runtime）都在重复的
 * {@code validate / testConnection / requireFactory / providerDefinitionsCache} 样板。
 * 活跃实例生命周期（active / fallback / close）因各 SPI 语义差异较大，留在具体子类自行维护。
 *
 * @param <C> provider context 类型
 * @param <F> SPI 工厂接口类型
 */
@Slf4j
public abstract class AbstractConfigurablePluginRegistry<C, F extends ConfigurablePluginProviderFactory<C>>
        extends
            AbstractPluginRegistry<String, F> {

    protected final C providerContext;
    protected final String spiFamily;

    /** 按 locale 分 key 的 provider definition 缓存,首次命中懒构造,后续 O(1)。 */
    private final Map<String, Map<String, PluginProviderDefinition>> providerDefinitionsCache =
            new ConcurrentHashMap<>();

    protected AbstractConfigurablePluginRegistry(Class<F> factoryClass, C providerContext, String spiFamily) {
        super(factoryClass);
        this.providerContext = providerContext;
        this.spiFamily = spiFamily;
    }

    /** 统一以 {@code getProvider()} 的大写形态做键，便于大小写不敏感查找。 */
    @Override
    protected String keyOf(F factory) {
        return normalize(factory.getProvider());
    }

    /**
     * 按 locale 返回 provider definition 映射;按语言 tag 分 key 缓存。
     * <p>未知 lang 归一化到 {@link SpiGuideLoader#DEFAULT_LANG},防止脏 {@code Accept-Language} 撑爆缓存。
     */
    public Map<String, PluginProviderDefinition> getProviderDefinitions(Locale locale) {
        return providerDefinitionsCache.computeIfAbsent(normalizeLang(locale),
                k -> buildDefinitions(Locale.forLanguageTag(k)));
    }

    /** 只保留可能存在 md 文件的受控 lang,其他一律落到默认。 */
    private static String normalizeLang(Locale locale) {
        if (locale == null) {
            return SpiGuideLoader.DEFAULT_LANG;
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        return SpiGuideLoader.KNOWN_LANGS.contains(lang) ? lang : SpiGuideLoader.DEFAULT_LANG;
    }

    private Map<String, PluginProviderDefinition> buildDefinitions(Locale locale) {
        Map<String, PluginProviderDefinition> defs = new LinkedHashMap<>();
        factories.forEach((key, factory) -> {
            // 每个 factory 只读一次 md,guide body + description 从同一份 SpiGuideFile 取
            SpiGuideFile guideFile = SpiGuideLoader.load(factory.family(), factory.getProvider(), locale);
            ProviderMetadata metadata = guideFile.description().isEmpty()
                    ? ProviderMetadata.empty()
                    : ProviderMetadata.of(guideFile.description());
            defs.put(key, new PluginProviderDefinition(
                    localizeParams(factory.params(), locale), guideFile.body(),
                    metadata, factory.priority(), true));
        });
        return java.util.Collections.unmodifiableMap(defs);
    }

    /**
     * 把 factory 声明的 params 中的 label / placeholder 当 i18n key 解析。
     * <p>未注册到 properties 的字面量(如 "API Key" / "Endpoint")在 {@code useCodeAsDefaultMessage=true}
     * 配合下原样返回,所以历史英文字面量无需逐个迁移到 bundle。
     */
    private static List<PluginParamDefinition> localizeParams(List<PluginParamDefinition> raw, Locale locale) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.stream()
                .map(p -> PluginParamDefinition.builder()
                        .name(p.getName())
                        .label(I18n.t(p.getLabel(), locale))
                        .type(p.getType())
                        .required(p.isRequired())
                        .placeholder(I18n.t(p.getPlaceholder(), locale))
                        .defaultValue(p.getDefaultValue())
                        .build())
                .toList();
    }

    /** 提供给 Controller 做 provider key 校验,不触发 md 加载 / definition 构造。 */
    public java.util.Set<String> providerKeys() {
        return factories.keySet();
    }

    public ValidationResult validate(String provider, Map<String, String> config) {
        return requireFactory(provider).validate(config != null ? config : Map.of());
    }

    public TestResult testConnection(String provider, Map<String, String> config) {
        return requireFactory(provider).testConnection(providerContext, config != null ? config : Map.of());
    }

    protected F requireFactory(String provider) {
        F factory = factories.get(normalize(provider));
        if (factory == null) {
            throw new NotFoundException(SpiErrorCode.PROVIDER_NOT_FOUND, spiFamily, provider);
        }
        return factory;
    }

    /** 统一的键规范化逻辑：大写，不改动 null。子类可覆写。 */
    protected String normalize(String raw) {
        return raw != null ? raw.toUpperCase(Locale.ROOT) : null;
    }

    /** 从 provider 配置 map 解析整型,null / 空白 / 格式错 → fallback。 */
    public static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
