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

package io.github.zzih.rudder.service.redaction.log;

import io.github.zzih.rudder.service.redaction.RedactionService;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 自定义 Logback 转换器,替换默认 {@code %msg} 渲染 —— 在写入 appender 之前对日志正文调用
 * {@link RedactionService#scrubText(String)}。
 * <p>
 * 在 logback-spring.xml 里注册:
 * <pre>{@code
 *   <conversionRule conversionWord="msg"
 *       converterClass="io.github.zzih.rudder.redaction.log.RedactingMessageConverter"/>
 * }</pre>
 * 之后所有 encoder pattern 里的 {@code %msg} / {@code %m} / {@code %message} 自动走脱敏。
 * <p>
 * Bridge 未就绪时(Spring 容器还没起好)返回原文。避免对启动日志造成阻塞。
 */
public class RedactingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String raw = super.convert(event);
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        RedactionService svc = LogRedactionBridge.get();
        if (svc == null) {
            return raw;
        }
        try {
            return svc.scrubText(raw);
        } catch (Throwable t) {
            // 日志链路必须绝对不能抛,兜底返回原文。
            return raw;
        }
    }
}
