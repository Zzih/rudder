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

package io.github.zzih.rudder.llm.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiMessage {

    /**
     * 角色："user"、"assistant" 或 "system"。
     */
    private String role;

    private String content;

    /**
     * 对于 tool_result 消息，content 是 JSON 数组而非字符串。
     * 设置后，序列化时优先使用此字段而非 {@link #content}。
     */
    @JsonIgnore
    private JsonNode contentNode;

    public AiMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }

    public static AiMessage withNode(String role, JsonNode contentNode) {
        AiMessage msg = new AiMessage();
        msg.setRole(role);
        msg.setContentNode(contentNode);
        return msg;
    }
}
