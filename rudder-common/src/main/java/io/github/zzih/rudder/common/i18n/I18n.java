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

package io.github.zzih.rudder.common.i18n;

import java.util.Locale;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 全局 i18n 静态门面,任何层（包括 SPI -api、common 内部、execution worker）都可以零依赖调用。
 *
 * <p>{@link MessageSource} 在 Spring 启动时由 {@code I18nConfig} 通过 {@link #bind(MessageSource)} 注入;
 * 未启动 / 单元测试场景未注入时,{@link #t} 直接返回 key,行为可预测,不会 NPE。
 *
 * <h2>规约</h2>
 * <ul>
 *   <li>白名单语言 {@link #SUPPORTED_LOCALES}: 与 {@code SpiGuideLoader.KNOWN_LANGS} 对齐</li>
 *   <li>未知 locale → 默认 zh,防止脏 {@code Accept-Language} 撑爆下游缓存</li>
 *   <li>找不到 key → 直接返回 key 字符串(配合前端肉眼可见,易于发现漏译)</li>
 * </ul>
 */
public final class I18n {

    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    public static final Set<String> SUPPORTED_LANGS = Set.of("zh", "en");

    private static volatile MessageSource messageSource;

    private I18n() {
    }

    /** Spring 启动时调用,把 {@link MessageSource} bean 绑定到静态门面。 */
    public static void bind(MessageSource ms) {
        messageSource = ms;
    }

    /** 当前线程 locale 下查 key。未注入或 miss → 返回 key 本身。 */
    public static String t(String key, Object... args) {
        return t(key, LocaleContextHolder.getLocale(), args);
    }

    /** 显式 locale 查 key。 */
    public static String t(String key, Locale locale, Object... args) {
        if (key == null) {
            return null;
        }
        MessageSource ms = messageSource;
        if (ms == null) {
            return key;
        }
        return ms.getMessage(key, args, key, normalize(locale));
    }

    /**
     * 把任意 locale 归一到白名单内;未知 / null → {@link #DEFAULT_LOCALE}。
     * 用 startsWith 兼容 {@code zh-CN / zh-TW / en-US}。
     */
    public static Locale normalize(Locale locale) {
        if (locale == null) {
            return DEFAULT_LOCALE;
        }
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        return SUPPORTED_LANGS.contains(lang) ? locale : DEFAULT_LOCALE;
    }
}
