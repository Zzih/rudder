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

package io.github.zzih.rudder.ai.orchestrator.tool;

import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 覆盖 Spring AI 默认的 {@link ToolCallbackResolver}(原实现在找不到工具时返回 null,会触发
 * {@code DefaultToolCallingManager} 抛 {@code IllegalStateException: No ToolCallback found}
 * 直接把 reactive stream 炸掉,agent turn 整个失败)。
 *
 * <p>Rudder 所有工具都通过 {@code ToolCallingChatOptions.toolCallbacks(...)} 显式注入,
 * 不依赖 Spring AI 的 resolver 机制去查找。resolver 只会在 LLM 幻觉出一个**不存在**的工具名
 * 时被调用——此时返回 {@link MissingToolCallback},让 LLM 在下一轮看到错误并自纠,不再硬崩。
 */
@Configuration
public class LenientToolCallbackResolverConfig {

    @Bean
    public ToolCallbackResolver toolCallbackResolver() {
        return MissingToolCallback::new;
    }
}
