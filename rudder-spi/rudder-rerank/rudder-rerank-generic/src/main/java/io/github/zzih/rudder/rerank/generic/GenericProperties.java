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

package io.github.zzih.rudder.rerank.generic;

import lombok.Data;

/**
 * Cohere 风格 rerank provider 配置(覆盖 Cohere/智谱/DashScope 新接口/Xinference/LocalAI)。
 *
 * <p>不抽 provider 特有字段,所有 provider 共用 4 个核心字段。特殊字段(如
 * Cohere {@code max_tokens_per_doc}、智谱 {@code return_raw_scores})
 * **本 SPI 实现暂不支持**,需要时再加。
 */
@Data
public class GenericProperties {

    /** API 鉴权 key。自部署服务可空。 */
    private String apiKey = "";

    /** 完整 endpoint URL,如 {@code https://api.cohere.com/v2/rerank}。 */
    private String endpoint = "";

    /** 模型 id,如 {@code rerank-v3.5} / {@code qwen3-rerank} / {@code bge-reranker-v2-m3}。 */
    private String model = "";
}
