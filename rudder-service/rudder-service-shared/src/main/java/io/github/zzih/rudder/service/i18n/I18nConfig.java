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

package io.github.zzih.rudder.service.i18n;

import io.github.zzih.rudder.common.i18n.I18n;

import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring 启动时装配 i18n 基础设施:5 类 properties bundle 的 {@link MessageSource}、{@link LocaleResolver}、
 * Bean Validation 的 {@link LocalValidatorFactoryBean},以及 {@link I18n} 静态门面绑定。
 *
 * <p>{@code useCodeAsDefaultMessage=true}: bundle miss 时直接吐 key,运维肉眼可见,易发现漏译。
 */
@Slf4j
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames("i18n/messages", "i18n/errors", "i18n/spi-params",
                "i18n/capabilities", "i18n/validation");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);
        I18n.bind(ms);
        log.info(
                "I18n bundles loaded: i18n/{{messages, errors, spi-params, capabilities, validation}}_{{zh, en}}.properties");
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH));
        r.setDefaultLocale(I18n.DEFAULT_LOCALE);
        return r;
    }

    /**
     * 让 hibernate-validator 用 Spring 的 {@link MessageSource} 解析 {@code @NotNull(message="{key}")} 占位,
     * 配合 {@code i18n/validation_<lang>.properties} 实现 Bean Validation 注解 message i18n。
     */
    @Bean
    public LocalValidatorFactoryBean validatorFactoryBean(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
}
