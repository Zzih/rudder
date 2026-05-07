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

package io.github.zzih.rudder.ai.eval;

/**
 * Eval 执行模式。默认 AGENT(最能代表真实场景)。
 */
public enum EvalMode {

    /** 带工具的 agent 流程,和生产 AgentExecutor 等价但不落库。 */
    AGENT,

    /** 纯对话,没工具(对应生产 TurnExecutor)。 */
    CHAT;

    public static EvalMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AGENT;
        }
        try {
            return EvalMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AGENT;
        }
    }
}
