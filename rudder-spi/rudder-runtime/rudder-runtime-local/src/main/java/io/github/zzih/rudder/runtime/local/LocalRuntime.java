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

package io.github.zzih.rudder.runtime.local;

import io.github.zzih.rudder.runtime.api.AbstractEngineRuntime;

import java.util.List;
import java.util.Map;

/**
 * Local runtime —— 不接管任何 TaskType,所有任务走 channel 默认工厂(原生 Task.handle())。
 * 仅暴露环境变量,由 Worker 注入到 ctx,供 Shell / Python / JAR 类任务读取。
 */
public class LocalRuntime extends AbstractEngineRuntime {

    public static final String PROVIDER_KEY = "LOCAL";

    public LocalRuntime(Map<String, String> envVars) {
        super(PROVIDER_KEY, envVars, List.of());
    }
}
