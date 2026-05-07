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

package io.github.zzih.rudder.common.enums.ai;

import java.util.Locale;

/** AI 会话模式。持久化到 {@code t_r_ai_session.mode} 的 VARCHAR 列。 */
public enum SessionMode {

    /** 纯聊天,不走 tool loop。 */
    CHAT,

    /** Agent 模式,允许 LLM 调用 tool / MCP。 */
    AGENT;

    /** 大小写/空白容错;null 或未知值 → 默认 CHAT。 */
    public static SessionMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CHAT;
        }
        try {
            return SessionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CHAT;
        }
    }

    public boolean is(String raw) {
        return this == from(raw);
    }
}
