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

/**
 * Logback converter 与 Spring 管理的 {@link RedactionService} 之间的静态桥。
 * <p>
 * Logback 在 Spring 容器启动之前就会解析 logback-spring.xml 并实例化 encoder / converter,
 * 因此无法直接注入 bean。启动时由一个 Spring 组件把 {@link RedactionService} 通过
 * {@link #install(RedactionService)} 注册进来;在注册之前 converter 走原样返回。
 */
public final class LogRedactionBridge {

    private static volatile RedactionService service;

    private LogRedactionBridge() {
    }

    public static void install(RedactionService svc) {
        service = svc;
    }

    public static RedactionService get() {
        return service;
    }
}
