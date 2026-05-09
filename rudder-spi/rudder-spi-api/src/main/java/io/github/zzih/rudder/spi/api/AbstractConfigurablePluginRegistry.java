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
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;
import io.github.zzih.rudder.spi.api.model.PluginProviderDefinition;
import io.github.zzih.rudder.spi.api.model.ProviderMetadata;
import io.github.zzih.rudder.spi.api.model.TestResult;
import io.github.zzih.rudder.spi.api.model.ValidationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户可配置 SPI 注册表基类。在 {@link AbstractPluginRegistry} 之上收拢:
 * <ul>
 *   <li>provider definition 缓存(按 locale)</li>
 *   <li>String JSON → Properties 反序列化派发(框架级,SPI 实现零脏活)</li>
 *   <li>统一的 validate / testConnection / canonicalize / deserialize 入口</li>
 * </ul>
 *
 * <p>active 实例的生命周期(active / fallback / close)因各 SPI 语义差异较大,
 * 留在 {@code <Spi>ConfigService} 与 {@link io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry}
 * 的具体子类(各 PluginManager)里维护。
 *
 * @param <C> provider context 类型
 * @param <F> SPI 工厂接口类型
 */
@Slf4j
public abstract class AbstractConfigurablePluginRegistry<C, F extends ConfigurablePluginProviderFactory<C, ?>>
        extends
            AbstractPluginRegistry<String, F> {

    protected final C providerContext;
    protected final String spiType;

    private final Map<String, Map<String, PluginProviderDefinition>> providerDefinitionsCache =
            new ConcurrentHashMap<>();

    protected AbstractConfigurablePluginRegistry(Class<F> factoryClass, C providerContext, String spiType) {
        super(factoryClass);
        this.providerContext = providerContext;
        this.spiType = spiType;
    }

    /** 大小写不敏感地以 provider 大写形式做 key。 */
    @Override
    protected String keyOf(F factory) {
        return normalize(factory.getProvider());
    }

    public Set<String> providerKeys() {
        return factories.keySet();
    }

    /** 按 locale 缓存 provider definition,首次构造,后续 O(1)。 */
    public Map<String, PluginProviderDefinition> getProviderDefinitions(Locale locale) {
        return providerDefinitionsCache.computeIfAbsent(normalizeLang(locale),
                k -> buildDefinitions(Locale.forLanguageTag(k)));
    }

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
            SpiGuideFile guideFile = SpiGuideLoader.load(factory.type(), factory.getProvider(), locale);
            ProviderMetadata metadata = guideFile.description().isEmpty()
                    ? ProviderMetadata.empty()
                    : ProviderMetadata.of(guideFile.description());
            defs.put(key, new PluginProviderDefinition(
                    localizeParams(factory.params(), locale), guideFile.body(),
                    metadata, factory.priority(), true));
        });
        return java.util.Collections.unmodifiableMap(defs);
    }

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

    // ==================== String JSON 入口(框架统一收口反序列化) ====================

    /**
     * 写入路径:JSON → P → factory.validate(P) → 失败抛 BizException;成功返回规范化 JSON 准备落库。
     * Controller 在调 ConfigService.saveDetail 前由基类调用,保证 DB 里 provider_params 是干净的 P 序列化。
     */
    public String validateAndCanonicalize(String provider, String providerParamsJson) {
        return doValidateAndCanonicalize(asTyped(requireFactory(provider)), providerParamsJson);
    }

    private <P> String doValidateAndCanonicalize(ConfigurablePluginProviderFactory<C, P> f, String json) {
        P props = deserializeProps(f, json);
        ValidationResult vr = f.validate(props);
        if (!vr.valid()) {
            String msg = vr.errors().isEmpty() ? "validation failed" : vr.errors().toString();
            throw new BizException(SpiErrorCode.PROVIDER_PARAMS_INVALID, msg);
        }
        return JsonUtils.toJson(props);
    }

    /** 回显路径:仅反序列化为 P 对象,Controller 直接塞 Response。 */
    public Object deserialize(String provider, String providerParamsJson) {
        return deserializeProps(asTyped(requireFactory(provider)), providerParamsJson);
    }

    /**
     * Controller {@code /validate} 端点用:返回 ValidationResult 给前端展示字段错误,**不抛异常**。
     * 反序列化失败时 ValidationResult.fail(null, msg)。
     */
    public ValidationResult validate(String provider, String providerParamsJson) {
        return doValidate(asTyped(requireFactory(provider)), providerParamsJson);
    }

    private <P> ValidationResult doValidate(ConfigurablePluginProviderFactory<C, P> f, String json) {
        P props;
        try {
            props = deserializeProps(f, json);
        } catch (BizException e) {
            return ValidationResult.fail(null, e.getMessage());
        }
        return f.validate(props);
    }

    /** 测试连接:反序列化 + factory.testConnection。失败返回 TestResult.failed,不抛。 */
    public TestResult testConnection(String provider, String providerParamsJson) {
        return doTestConnection(asTyped(requireFactory(provider)), providerParamsJson);
    }

    private <P> TestResult doTestConnection(ConfigurablePluginProviderFactory<C, P> f, String json) {
        P props;
        try {
            props = deserializeProps(f, json);
        } catch (BizException e) {
            return TestResult.failed(e.getMessage(), 0L);
        }
        return f.testConnection(providerContext, props);
    }

    /** wildcard F → typed,本类 String JSON 入口和子类 typed doCreate 共用的 capture 工具。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <P> ConfigurablePluginProviderFactory<C, P> asTyped(F factory) {
        return (ConfigurablePluginProviderFactory) factory;
    }

    /**
     * 给子类 PluginManager 用的 capture helper:从已经 typed 的 factory(子类 doCreate 里 capture 出来的)
     * 反序列化 JSON 到 P。各 SPI PluginManager 在自己的 typed doCreate 里调用本方法。
     */
    protected <P> P deserializeProps(ConfigurablePluginProviderFactory<C, P> typedFactory, String providerParamsJson) {
        try {
            String json = providerParamsJson == null || providerParamsJson.isBlank()
                    ? "{}"
                    : providerParamsJson;
            return JsonUtils.fromJson(json, typedFactory.propertiesClass());
        } catch (Exception e) {
            throw new BizException(SpiErrorCode.PROVIDER_PARAMS_MALFORMED, e.getMessage());
        }
    }

    // ==================== 工具 ====================

    protected F requireFactory(String provider) {
        F factory = factories.get(normalize(provider));
        if (factory == null) {
            throw new NotFoundException(SpiErrorCode.PROVIDER_NOT_FOUND, spiType, provider);
        }
        return factory;
    }

    protected String normalize(String raw) {
        return raw != null ? raw.toUpperCase(Locale.ROOT) : null;
    }

    /** 从 provider 配置 map 解析整型,null / 空白 / 格式错 → fallback。仍保留给极少数特例使用,新代码请用 P 字段。 */
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
