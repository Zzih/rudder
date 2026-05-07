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

package io.github.zzih.rudder.llm.api.callback;

public interface LlmStreamCallback {

    void onToken(String token);

    void onComplete(String fullContent);

    void onError(Throwable error);

    /**
     * Provider 报告的 token 用量。流结束前回调(Claude 是 message_delta,OpenAI 需开 stream_options)。
     * 默认空实现,老 provider 不强制回调;实现此回调的 provider 让 Executor 能落 t_r_ai_message.tokens 列。
     */
    default void onUsage(Integer promptTokens, Integer completionTokens) {
        // no-op
    }
}
