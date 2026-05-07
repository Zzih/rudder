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

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Spring 启动后把 {@link RedactionService} bean 注册到 {@link LogRedactionBridge},
 * 让 logback 的 {@code RedactingMessageConverter} 能够走脱敏。
 */
@Component
@RequiredArgsConstructor
public class LogRedactionInstaller {

    private final RedactionService redactionService;

    @PostConstruct
    public void install() {
        LogRedactionBridge.install(redactionService);
    }
}
