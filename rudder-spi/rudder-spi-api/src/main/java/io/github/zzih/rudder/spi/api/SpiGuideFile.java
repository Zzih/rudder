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

/**
 * 从 classpath 的 {@code spi-guide/<family>-<provider>.<lang>.md} 解析出来的 provider 文案。
 * <p>
 * Markdown 文件头允许带 YAML-like front-matter,解析后 {@link #description} = 第一行短说明,
 * {@link #body} = front-matter 之后的 guide 正文。
 *
 * <pre>
 * ---
 * description: DeepSeek 原生 API
 * ---
 *
 * ## DeepSeek 元数据
 *
 * ...guide 正文...
 * </pre>
 */
public record SpiGuideFile(String description, String body) {

    public static final SpiGuideFile EMPTY = new SpiGuideFile("", "");
}
