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

package io.github.zzih.rudder.api.config;

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 全局 Jackson 配置(Spring Boot 4 / Jackson 3):HTTP 响应里的 LocalDateTime / LocalDate
 * 统一格式化为 "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd"。
 * <p>
 * 只影响 Spring MVC 的出入参序列化。业务代码里直接 {@code new ObjectMapper()}(Jackson 2)
 * 的实例不受影响。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Jackson 2 {@link ObjectMapper} bean —— Boot 4 默认只 autoconfigure Jackson 3,
     * 业务代码(MCP / SSO / Gitea / MetadataSync 等)仍 {@code @Autowired ObjectMapper}
     * 需要 Jackson 2,由此补一个。共用 {@code JsonUtils} 里配好的实例(带 JavaTimeModule 等)。
     */
    @Bean
    public ObjectMapper jackson2ObjectMapper() {
        return JsonUtils.getObjectMapper();
    }

    @Bean
    public JsonMapperBuilderCustomizer rudderJsonMapperCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("rudder-datetime")
                    .addSerializer(new LocalDateTimeSerializer(DATETIME_FORMATTER))
                    .addDeserializer(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(DATETIME_FORMATTER))
                    .addSerializer(new LocalDateSerializer(DATE_FORMATTER))
                    .addDeserializer(java.time.LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
            builder.addModule(module);
        };
    }
}
