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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 按 classpath 约定加载 provider guide:{@code spi-guide/<family>-<provider>.<lang>.md}。
 * <p>
 * 文件名 family 做前缀,避免跨 SPI 同名 provider(OPENAI / LARK / LOCAL)在 fat jar 里被覆盖。
 * 请求 locale miss 退化到 {@link #DEFAULT_LANG};再 miss 返回 {@link SpiGuideFile#EMPTY}。
 * <p>
 * 加载失败永远不抛异常,只 WARN —— provider 文档缺失不该让后台挂。
 */
@Slf4j
public final class SpiGuideLoader {

    /** 默认语言:所有 provider 至少要有这一份。 */
    public static final String DEFAULT_LANG = "zh";

    /** 项目支持的语言白名单。未知 lang(脏 Accept-Language)统一归一化到 {@link #DEFAULT_LANG}。 */
    public static final Set<String> KNOWN_LANGS = Set.of("zh", "en");

    private SpiGuideLoader() {
    }

    /**
     * 按 family + provider + locale 加载。先按请求 lang 找,miss 退 {@link #DEFAULT_LANG}。
     * 未知 lang 归一到 DEFAULT_LANG。
     */
    public static SpiGuideFile load(String family, String provider, Locale locale) {
        if (provider == null || provider.isBlank() || family == null || family.isBlank()) {
            return SpiGuideFile.EMPTY;
        }
        String lang = locale == null ? DEFAULT_LANG : locale.getLanguage().toLowerCase(Locale.ROOT);
        if (!KNOWN_LANGS.contains(lang)) {
            lang = DEFAULT_LANG;
        }
        String p = provider.toLowerCase(Locale.ROOT);
        String f = family.toLowerCase(Locale.ROOT).replace('_', '-');

        SpiGuideFile hit = readIfExists("spi-guide/" + f + "-" + p + "." + lang + ".md");
        if (hit != SpiGuideFile.EMPTY) {
            return hit;
        }
        if (!DEFAULT_LANG.equals(lang)) {
            return readIfExists("spi-guide/" + f + "-" + p + "." + DEFAULT_LANG + ".md");
        }
        return SpiGuideFile.EMPTY;
    }

    private static SpiGuideFile readIfExists(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = SpiGuideLoader.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                return SpiGuideFile.EMPTY;
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("SPI guide load failed path={}: {}", path, e.getMessage());
            return SpiGuideFile.EMPTY;
        }
    }

    private static final Pattern LINE_SPLIT = Pattern.compile("\\r?\\n");

    /**
     * 从带可选 front-matter 的 markdown 抽取 {description, body}。
     * front-matter 语法:文件头 {@code ---} 起 + {@code ---} 止,每行 {@code key: value},
     * 目前只识别 {@code description} 一个键;更多键日后按需扩展。
     */
    static SpiGuideFile parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return SpiGuideFile.EMPTY;
        }
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw; // strip BOM
        if (!text.startsWith("---")) {
            return new SpiGuideFile("", text);
        }
        int bodyStart = findFrontMatterEnd(text);
        if (bodyStart < 0) {
            return new SpiGuideFile("", text);
        }
        String frontMatter = text.substring(3, bodyStart - 3).trim();
        String body = text.substring(bodyStart);
        if (body.startsWith("\r\n")) {
            body = body.substring(2);
        } else if (body.startsWith("\n")) {
            body = body.substring(1);
        }
        String description = "";
        for (String line : LINE_SPLIT.split(frontMatter)) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if ("description".equals(key)) {
                description = value;
            }
        }
        return new SpiGuideFile(description, body);
    }

    /** 找到 front-matter 结束的 {@code ---} 之后的位置;没找到返回 -1。 */
    private static int findFrontMatterEnd(String text) {
        int from = 3;
        while (from < text.length()) {
            int nl = text.indexOf('\n', from);
            if (nl < 0) {
                return -1;
            }
            String line = text.substring(from, nl).trim();
            if ("---".equals(line)) {
                return nl + 1;
            }
            from = nl + 1;
        }
        return -1;
    }
}
