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

import io.github.zzih.rudder.service.redaction.RedactionService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

/**
 * 出站脱敏 Advisor:在 ChatClient 返回前 scrub AI 生成文本里的 PII(邮箱 / 手机 / token 等)。
 * <p>
 * 只改 text,保留 tool_calls / metadata / usage。tool input(LLM 准备调用工具的 JSON)不 scrub,
 * 避免 mask 掉合法的字段值。规则由 RedactionService 从 DB 统一加载(平台级,不分 workspace)。
 */
public class RedactionAdvisor implements BaseAdvisor {

    private final RedactionService redactionService;
    private final int order;

    public RedactionAdvisor(RedactionService redactionService) {
        this(redactionService, Ordered.LOWEST_PRECEDENCE - 10);
    }

    public RedactionAdvisor(RedactionService redactionService, int order) {
        this.redactionService = redactionService;
        this.order = order;
    }

    @Override
    public String getName() {
        return "RudderRedactionAdvisor";
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
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            return chatClientResponse;
        }
        List<Generation> scrubbed = new ArrayList<>(resp.getResults().size());
        boolean changed = false;
        for (Generation g : resp.getResults()) {
            AssistantMessage in = g.getOutput();
            String text = in == null ? null : in.getText();
            if (text == null || text.isEmpty()) {
                scrubbed.add(g);
                continue;
            }
            String cleaned = redactionService.scrubText(text);
            if (cleaned == null || cleaned.equals(text)) {
                scrubbed.add(g);
                continue;
            }
            changed = true;
            AssistantMessage rebuilt = AssistantMessage.builder()
                    .content(cleaned)
                    .toolCalls(in.getToolCalls() == null ? List.of() : in.getToolCalls())
                    .media(in.getMedia() == null ? List.of() : in.getMedia())
                    .properties(in.getMetadata() == null ? java.util.Map.of() : in.getMetadata())
                    .build();
            scrubbed.add(new Generation(rebuilt, g.getMetadata()));
        }
        if (!changed) {
            return chatClientResponse;
        }
        ChatResponse newResp = ChatResponse.builder().from(resp).generations(scrubbed).build();
        return chatClientResponse.mutate().chatResponse(newResp).build();
    }
}
