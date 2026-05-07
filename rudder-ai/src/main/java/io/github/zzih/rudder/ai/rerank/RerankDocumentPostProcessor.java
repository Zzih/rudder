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

package io.github.zzih.rudder.ai.rerank;

import io.github.zzih.rudder.rerank.api.RerankClient;
import io.github.zzih.rudder.rerank.api.RerankResult;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link DocumentPostProcessor} 桥接 —— 把 Rudder {@link RerankClient} 接进
 * {@code RetrievalAugmentationAdvisor} 的 Post-Retrieval 阶段。
 * <p>
 * 流程:抽取 chunk text → 调 RerankClient → 按 score 降序 + topN 截断 → 把 score 写回 metadata
 * 方便上层调试。
 *
 * <p><b>失败降级</b>: rerank 调用失败不阻断主对话,日志降级返回原列表(等价于无 rerank)。
 */
@Slf4j
@RequiredArgsConstructor
public class RerankDocumentPostProcessor implements DocumentPostProcessor {

    public static final String META_RERANK_SCORE = "rerank_score";
    public static final String META_RERANK_MODEL = "rerank_model";

    private final RerankClient rerankClient;
    private final int topN;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty() || query == null || query.text() == null) {
            return documents;
        }

        List<String> texts = new ArrayList<>(documents.size());
        for (Document d : documents) {
            String t = d.getText();
            texts.add(t == null ? "" : t);
        }

        List<RerankResult> ranked;
        try {
            ranked = rerankClient.rerank(query.text(), texts, topN <= 0 ? documents.size() : topN);
        } catch (Exception e) {
            // rerank 是 quality enhancement,不该阻断主链路。失败 → 退回原 list
            log.warn("rerank failed, falling back to original ordering: {}", e.getMessage());
            return documents;
        }
        if (ranked.isEmpty()) {
            return documents;
        }

        String modelId = rerankClient.modelId();
        List<Document> out = new ArrayList<>(ranked.size());
        for (RerankResult r : ranked) {
            int idx = r.index();
            if (idx < 0 || idx >= documents.size()) {
                // provider 返回了越界下标,跳过(不该发生,防御式)
                continue;
            }
            Document src = documents.get(idx);
            // 把 rerank score 写到 metadata 副本里(Document 不可变,要 mutate 必须 rebuild)
            Document scored = src.mutate()
                    .metadata(META_RERANK_SCORE, r.score())
                    .metadata(META_RERANK_MODEL, modelId)
                    .build();
            out.add(scored);
        }
        return out;
    }
}
