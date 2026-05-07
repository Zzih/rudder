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

package io.github.zzih.rudder.ai.orchestrator;

import io.github.zzih.rudder.ai.orchestrator.message.MessagePersistence;

/**
 * Token 刷盘器。
 * <p>
 * Provider 一秒可能吐几十个 token,直接 update DB 太贵。
 * 累积到阈值(字符数 或 时间)才刷一次;finish/flush 时强制落库。
 * <p>
 * 非线程安全 —— 每个 turn 独立一个实例。
 */
public final class TokenFlusher {

    private static final int FLUSH_CHARS = 256;
    private static final long FLUSH_INTERVAL_MS = 200;

    private final long messageId;
    private final MessagePersistence persistence;

    private final StringBuilder buffer = new StringBuilder();
    private int lastFlushedLen = 0;
    private long lastFlushAt = System.currentTimeMillis();
    private boolean markedStreaming = false;

    public TokenFlusher(long messageId, MessagePersistence persistence) {
        this.messageId = messageId;
        this.persistence = persistence;
    }

    /** 追加 token,必要时触发一次 flush。 */
    public void append(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        buffer.append(token);
        if (!markedStreaming) {
            persistence.markStreaming(messageId);
            markedStreaming = true;
        }
        int unflushed = buffer.length() - lastFlushedLen;
        long now = System.currentTimeMillis();
        if (unflushed >= FLUSH_CHARS || (unflushed > 0 && now - lastFlushAt >= FLUSH_INTERVAL_MS)) {
            flush();
        }
    }

    /** 立即刷盘。 */
    public void flush() {
        if (buffer.length() == lastFlushedLen) {
            return;
        }
        persistence.flushContent(messageId, buffer.toString());
        lastFlushedLen = buffer.length();
        lastFlushAt = System.currentTimeMillis();
    }

    /** 当前缓冲的全文(供 finishMessage 最终落库)。 */
    public String currentContent() {
        return buffer.toString();
    }
}
