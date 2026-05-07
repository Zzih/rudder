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

package io.github.zzih.rudder.llm.api.springai;

import io.github.zzih.rudder.llm.api.LlmClient;
import io.github.zzih.rudder.llm.api.callback.LlmStreamCallback;
import io.github.zzih.rudder.llm.api.model.AiMessage;
import io.github.zzih.rudder.llm.api.model.LlmChatRequest;
import io.github.zzih.rudder.llm.api.model.LlmCompleteRequest;
import io.github.zzih.rudder.llm.api.model.LlmResponse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用 LlmClient 实现,底层委托给 Spring AI {@link ChatModel}。
 * <p>
 * 本类只处理 CHAT 模式的 stream/complete —— AGENT 模式由 {@code AgentExecutor} 直接拿 {@link ChatModel}
 * 构 {@code ChatClient} + Advisor 链,不再走 {@code LlmClient} 契约。
 */
@Slf4j
public class SpringAiBackedLlmClient implements LlmClient {

    private final ChatModel chatModel;

    public SpringAiBackedLlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }

    // ==================== 流式 chat ====================

    @Override
    public void chat(LlmChatRequest request, LlmStreamCallback callback) {
        try {
            Prompt prompt = buildChatPrompt(request);
            StringBuilder full = new StringBuilder();
            int[] usage = new int[2]; // [prompt, completion]

            chatModel.stream(prompt).doOnNext(resp -> {
                Generation g = resp.getResult();
                if (g != null) {
                    String delta = g.getOutput().getText();
                    if (delta != null && !delta.isEmpty()) {
                        full.append(delta);
                        callback.onToken(delta);
                    }
                }
                Usage u = resp.getMetadata() != null ? resp.getMetadata().getUsage() : null;
                if (u != null) {
                    if (u.getPromptTokens() != null) {
                        usage[0] = u.getPromptTokens();
                    }
                    if (u.getCompletionTokens() != null) {
                        usage[1] = u.getCompletionTokens();
                    }
                }
            }).blockLast();

            callback.onUsage(usage[0] > 0 ? usage[0] : null, usage[1] > 0 ? usage[1] : null);
            callback.onComplete(full.toString());
        } catch (Exception e) {
            log.error("Spring AI chat stream error", e);
            callback.onError(e);
        }
    }

    // ==================== 单次 complete ====================

    @Override
    public LlmResponse complete(LlmCompleteRequest request) {
        try {
            List<Message> msgs = new ArrayList<>();
            if (request.getContext() != null && request.getContext().getLanguage() != null) {
                msgs.add(new SystemMessage("Respond in " + languageLabel(request.getContext().getLanguage()) + "."));
            }
            msgs.add(new UserMessage(request.getPrompt()));
            ChatResponse resp = chatModel.call(new Prompt(msgs));
            LlmResponse out = new LlmResponse();
            AssistantMessage m = resp.getResult().getOutput();
            out.setContent(m.getText() == null ? "" : m.getText());
            Usage u = resp.getMetadata() != null ? resp.getMetadata().getUsage() : null;
            if (u != null && u.getCompletionTokens() != null) {
                out.setTokenUsage(u.getCompletionTokens().longValue());
            }
            return out;
        } catch (Exception e) {
            log.error("Spring AI complete error", e);
            LlmResponse err = new LlmResponse();
            err.setContent("AI service error: " + e.getMessage());
            return err;
        }
    }

    // ==================== 内部 ====================

    private Prompt buildChatPrompt(LlmChatRequest request) {
        List<Message> msgs = new ArrayList<>();
        String system = request.getSystemPrompt();
        if (system != null && !system.isEmpty()) {
            msgs.add(new SystemMessage(system));
        }
        if (request.getHistory() != null) {
            for (AiMessage h : request.getHistory()) {
                msgs.add(toSpringMessageSimple(h));
            }
        }
        if (request.getUserMessage() != null) {
            msgs.add(new UserMessage(nullSafe(request.getUserMessage().getContent())));
        }
        return new Prompt(msgs);
    }

    /** CHAT 模式:历史纯文本,工具调用走 AGENT 模式的 ChatClient 路径。 */
    private Message toSpringMessageSimple(AiMessage m) {
        String role = m.getRole() == null ? "user" : m.getRole();
        String content = nullSafe(m.getContent());
        return switch (role) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    private static String languageLabel(String lang) {
        return switch (lang) {
            case "zh" -> "Chinese (简体中文)";
            case "en" -> "English";
            default -> lang;
        };
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
