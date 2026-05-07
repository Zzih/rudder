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

package io.github.zzih.rudder.llm.api;

import io.github.zzih.rudder.llm.api.callback.LlmStreamCallback;
import io.github.zzih.rudder.llm.api.model.LlmChatRequest;
import io.github.zzih.rudder.llm.api.model.LlmCompleteRequest;
import io.github.zzih.rudder.llm.api.model.LlmResponse;

public interface LlmClient extends AutoCloseable {

    /** 健康检查。默认 UNKNOWN（provider 未实现）。 */
    default io.github.zzih.rudder.spi.api.model.HealthStatus healthCheck() {
        return io.github.zzih.rudder.spi.api.model.HealthStatus.unknown();
    }

    @Override
    default void close() {
    }

    /**
     * 返回底层 Spring AI {@link org.springframework.ai.chat.model.ChatModel},
     * 供 AgentExecutor 构造 {@code ChatClient} + {@code ToolCallback} + Advisor 链。
     * 非 Spring AI 支撑的 provider 返回 null。
     */
    default org.springframework.ai.chat.model.ChatModel getChatModel() {
        return null;
    }

    /**
     * 流式 SSE 对话。
     */
    void chat(LlmChatRequest request, LlmStreamCallback callback);

    /**
     * 单次补全。
     */
    LlmResponse complete(LlmCompleteRequest request);
}
