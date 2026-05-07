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

import io.github.zzih.rudder.ai.dto.AiEvalCaseDTO;
import io.github.zzih.rudder.ai.dto.AiEvalRunDTO;
import io.github.zzih.rudder.ai.orchestrator.Ulid;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.AiEvalCaseDao;
import io.github.zzih.rudder.dao.dao.AiEvalRunDao;
import io.github.zzih.rudder.dao.entity.AiEvalCase;
import io.github.zzih.rudder.dao.entity.AiEvalRun;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 评测服务:管理 case + 触发批次执行。
 * <p>
 * 每个 case 通过 {@link EvalExecutor} 跑一次真实 agent/chat 流程(带 system prompt /
 * tool chain / RAG),结果经 {@link EvalVerifier} 按 {@link ExpectedSpec} 多维度校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {

    private final AiEvalCaseDao caseDao;
    private final AiEvalRunDao runDao;
    private final EvalExecutor evalExecutor;
    private final EvalVerifier evalVerifier;
    private final AiSemanticEvaluator semanticEvaluator;

    // ======================== Case CRUD ========================

    public AiEvalCase getCase(Long id) {
        return caseDao.selectById(id);
    }

    public List<AiEvalCase> listCases(String category, Boolean activeOnly) {
        return caseDao.selectAll(category, activeOnly);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiEvalCase> pageCases(
                                                                              String category, Boolean activeOnly,
                                                                              int pageNum, int pageSize) {
        return caseDao.selectPage(category, activeOnly, pageNum, pageSize);
    }

    public AiEvalCase createCase(AiEvalCase entity) {
        if (entity.getActive() == null) {
            entity.setActive(true);
        }
        if (entity.getMode() == null || entity.getMode().isBlank()) {
            entity.setMode(EvalMode.AGENT.name());
        }
        caseDao.insert(entity);
        return entity;
    }

    public void updateCase(Long id, AiEvalCase entity) {
        entity.setId(id);
        caseDao.updateById(entity);
    }

    public void deleteCase(Long id) {
        caseDao.deleteById(id);
    }

    // ======================== Batch Run ========================

    public BatchResult runBatch(String category) {
        String batchId = Ulid.newUlid();
        List<AiEvalCase> cases = caseDao.selectAll(category, true);

        int passed = 0;
        int failed = 0;
        List<AiEvalRun> runs = new ArrayList<>();
        for (AiEvalCase c : cases) {
            AiEvalRun run = runOne(batchId, c);
            runs.add(run);
            if (Boolean.TRUE.equals(run.getPassed())) {
                passed++;
            } else {
                failed++;
            }
        }
        return new BatchResult(batchId, cases.size(), passed, failed,
                BeanConvertUtils.convertList(runs, AiEvalRunDTO.class));
    }

    public List<AiEvalRun> getBatch(String batchId) {
        return runDao.selectByBatch(batchId);
    }

    public List<AiEvalRun> getCaseHistory(Long caseId, int limit) {
        return runDao.selectByCase(caseId, limit <= 0 ? 20 : limit);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiEvalRun> pageCaseHistory(
                                                                                   Long caseId, int pageNum,
                                                                                   int pageSize) {
        return runDao.selectPageByCase(caseId, pageNum, pageSize);
    }

    // ======================== internal ========================

    private AiEvalRun runOne(String batchId, AiEvalCase c) {
        OneshotResult result = evalExecutor.run(c);
        ExpectedSpec spec = ExpectedSpec.parse(c.getExpectedJson());
        EvalVerifier.Verdict verdict = evalVerifier.verify(result, spec);

        // 语义评分(LLM-as-judge): 跑 RelevancyEvaluator + FactCheckingEvaluator
        // 失败降级返回 unavailable, score=null, 不影响规则验证 pass/fail
        AiSemanticEvaluator.Verdict semantic = semanticEvaluator.evaluate(c.getPrompt(), result);

        AiEvalRun run = new AiEvalRun();
        run.setBatchId(batchId);
        run.setCaseId(c.getId());
        run.setProvider(result.getProvider());
        run.setModel(result.getModel());
        run.setPassed(verdict.isPassed());
        // 优先用 semantic score (Spring AI Evaluator); 不可用时退回旧二值分
        run.setScore(semantic.isAvailable()
                ? BigDecimal.valueOf(semantic.getScore())
                : (verdict.isPassed() ? BigDecimal.valueOf(100) : BigDecimal.ZERO));
        run.setFinalText(result.getFinalText());
        run.setToolCallsJson(JsonUtils.toJson(result.getToolCalls()));
        // failReasons = 规则失败原因 + 语义评估反馈合并
        List<String> reasons = new ArrayList<>(verdict.getFailures());
        reasons.addAll(semantic.getFeedback());
        run.setFailReasonsJson(JsonUtils.toJson(reasons));
        run.setLatencyMs(result.getLatencyMs());
        run.setPromptTokens(result.getPromptTokens());
        run.setCompletionTokens(result.getCompletionTokens());
        runDao.insert(run);
        return run;
    }

    @Data
    @AllArgsConstructor
    public static class BatchResult {

        private String batchId;
        private int total;
        private int passed;
        private int failed;
        private List<AiEvalRunDTO> runs;
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public AiEvalCaseDTO getCaseDetail(Long id) {
        return BeanConvertUtils.convert(getCase(id), AiEvalCaseDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiEvalCaseDTO> pageCasesDetail(
                                                                                       String category,
                                                                                       Boolean activeOnly,
                                                                                       int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(pageCases(category, activeOnly, pageNum, pageSize), AiEvalCaseDTO.class);
    }

    public AiEvalCaseDTO createCaseDetail(AiEvalCaseDTO body) {
        AiEvalCase entity = BeanConvertUtils.convert(body, AiEvalCase.class);
        return BeanConvertUtils.convert(createCase(entity), AiEvalCaseDTO.class);
    }

    public void updateCaseDetail(Long id, AiEvalCaseDTO body) {
        updateCase(id, BeanConvertUtils.convert(body, AiEvalCase.class));
    }

    public List<AiEvalRunDTO> getBatchDetail(String batchId) {
        return BeanConvertUtils.convertList(getBatch(batchId), AiEvalRunDTO.class);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiEvalRunDTO> pageCaseHistoryDetail(
                                                                                            Long caseId, int pageNum,
                                                                                            int pageSize) {
        return BeanConvertUtils.convertPage(pageCaseHistory(caseId, pageNum, pageSize), AiEvalRunDTO.class);
    }
}
