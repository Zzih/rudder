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

package io.github.zzih.rudder.spi.api.model;

import lombok.Builder;
import lombok.Data;

/**
 * 通用 plugin 配置字段元数据,各 SPI 的 {@code params()} 返回此列表,前端据此动态渲染表单。
 *
 * <p>{@link #type} 取 {@code input / password / number / boolean / textarea / select} 之一,
 * 或特殊值 {@link #TYPE_RAW_JSON}:整段 freeform JSON,字段 value 即为最终序列化进 DB 的 plugin params,
 * 不嵌套到 {name: value} map。仅允许 {@code params()} 返回单 entry 时使用,提交时 value 直送。
 */
@Data
@Builder
public class PluginParamDefinition {

    public static final String TYPE_RAW_JSON = "rawJson";

    private String name;
    private String label;
    @Builder.Default
    private String type = "input";
    @Builder.Default
    private boolean required = false;
    private String placeholder;
    private String defaultValue;
}
