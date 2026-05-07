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

import io.github.zzih.rudder.service.redaction.RedactionService;

/**
 * 流式脱敏缓冲:PII 模式往往跨 LLM token 边界(邮箱 abc@example.com 可能被拆成 "abc@" + "example.com"),
 * 如果每个 delta 单独 scrub 会漏检。
 * <p>
 * 策略:保留末尾 {@link #HOLD_BACK} 字符不吐出,前面部分 scrub 后交给调用方。
 * flush() 时把剩余 buffer 整体 scrub 掉最后一批。HOLD_BACK 必须大于单个 PII 模式最长长度。
 */
final class StreamingRedactor {

    /**
     * 保留的尾部字符数。覆盖常见 PII 最长 pattern:
     * 身份证 18、手机 11、邮箱 ~60、sk- / ghp_ token ~40 — 128 安全且不显著影响 UX。
     */
    private static final int HOLD_BACK = 128;

    private final RedactionService redactionService;
    private final StringBuilder buf = new StringBuilder();

    StreamingRedactor(RedactionService redactionService) {
        this.redactionService = redactionService;
    }

    /**
     * 追加一段 LLM delta,返回可以 emit 的已脱敏文本(可能为空)。
     */
    String append(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        buf.append(chunk);
        if (buf.length() <= HOLD_BACK) {
            return "";
        }
        int emitLen = buf.length() - HOLD_BACK;
        String part = buf.substring(0, emitLen);
        buf.delete(0, emitLen);
        return scrub(part);
    }

    /** 流结束:把尾部 buffer 全部 scrub 后 emit。 */
    String flush() {
        if (buf.length() == 0) {
            return "";
        }
        String s = buf.toString();
        buf.setLength(0);
        return scrub(s);
    }

    private String scrub(String s) {
        if (redactionService == null) {
            return s;
        }
        String cleaned = redactionService.scrubText(s);
        return cleaned == null ? s : cleaned;
    }
}
