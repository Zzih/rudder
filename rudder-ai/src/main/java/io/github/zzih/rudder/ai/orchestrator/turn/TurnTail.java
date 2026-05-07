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

package io.github.zzih.rudder.ai.orchestrator.turn;

import io.github.zzih.rudder.ai.orchestrator.TokenFlusher;
import io.github.zzih.rudder.common.i18n.I18n;

import lombok.extern.slf4j.Slf4j;

/** Turn 结束阶段的公共尾部处理:消除 AgentExecutor / TurnExecutor 重复块。 */
@Slf4j
public final class TurnTail {

    private TurnTail() {
    }

    /** LLM finishReason 命中 length / max_tokens 时,在内容尾部追加用户可见提示 + 写日志。 */
    public static void appendLengthLimitTail(String finishReason, String turnId,
                                             TokenFlusher flusher, TurnEventSink sink) {
        if (!"length".equalsIgnoreCase(finishReason)
                && !"max_tokens".equalsIgnoreCase(finishReason)) {
            return;
        }
        log.warn("turn {} hit output length limit (finishReason={}), reply truncated. "
                + "Consider raising provider_params.maxTokens.", turnId, finishReason);
        // 按当前 Accept-Language 解析,handler 已绑定 LocaleContextHolder
        String tail = I18n.t("msg.ai.output.lengthLimitTail");
        flusher.append(tail);
        sink.emit(new TurnEvent.Token(tail));
    }
}
