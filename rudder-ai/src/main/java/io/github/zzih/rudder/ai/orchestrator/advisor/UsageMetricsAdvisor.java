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

package io.github.zzih.rudder.ai.orchestrator.advisor;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * Usage 累加 Advisor。AGENT 多轮 tool-call 期间 {@code ChatClient} 可能多次触发 ChatModel 调用,
 * 每次都会经过 Advisor 链 {@code after}。此处把每轮的 promptTokens / completionTokens 累加到
 * 共享计数器上,turn 结束时 AgentExecutor 读取累计值落库。
 */
public class UsageMetricsAdvisor implements BaseAdvisor {

    private final AtomicInteger promptTokens;
    private final AtomicInteger completionTokens;
    private final int order;

    public UsageMetricsAdvisor(AtomicInteger promptTokens, AtomicInteger completionTokens) {
        this(promptTokens, completionTokens, Ordered.LOWEST_PRECEDENCE);
    }

    public UsageMetricsAdvisor(AtomicInteger promptTokens, AtomicInteger completionTokens, int order) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.order = order;
    }

    @Override
    public String getName() {
        return "RudderUsageMetricsAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse resp = chatClientResponse.chatResponse();
        if (resp == null || resp.getMetadata() == null) {
            return chatClientResponse;
        }
        Usage u = resp.getMetadata().getUsage();
        if (u == null) {
            return chatClientResponse;
        }
        if (u.getPromptTokens() != null) {
            promptTokens.addAndGet(u.getPromptTokens());
        }
        if (u.getCompletionTokens() != null) {
            completionTokens.addAndGet(u.getCompletionTokens());
        }
        return chatClientResponse;
    }
}
