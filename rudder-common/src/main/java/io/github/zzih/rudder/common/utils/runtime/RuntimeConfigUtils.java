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

package io.github.zzih.rudder.common.utils.runtime;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 把用户在平台配置页填入的单个 textarea（.properties 格式：KEY=VALUE，# 开头为注释）
 * 解析成扁平的 Map，让 provider 的 buildProperties 之类的逻辑继续按点号 key 取值。
 */
public final class RuntimeConfigUtils {

    private RuntimeConfigUtils() {
    }

    public static Map<String, String> parseProperties(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid runtime config: " + e.getMessage(), e);
        }
        // Properties.load 会吃掉 '=' 后的前导空格但保留尾随空格；统一 trim 一下避免用户误写 "region=us-east-1 " 传错给 SDK
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            result.put(key, value != null ? value.trim() : null);
        }
        return result;
    }
}
