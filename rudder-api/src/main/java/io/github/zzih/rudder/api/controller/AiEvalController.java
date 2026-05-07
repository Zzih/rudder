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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.ai.dto.AiEvalCaseDTO;
import io.github.zzih.rudder.ai.eval.EvalService;
import io.github.zzih.rudder.ai.eval.EvalService.BatchResult;
import io.github.zzih.rudder.api.request.AiEvalCaseRequest;
import io.github.zzih.rudder.api.response.AiEvalCaseResponse;
import io.github.zzih.rudder.api.response.AiEvalRunResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AI 评测管理:用例 CRUD + 批次执行。仅 SUPER_ADMIN。 */
@RestController
@RequestMapping("/api/ai/eval")
@RequiredArgsConstructor
@RequireRole(RoleType.SUPER_ADMIN)
public class AiEvalController {

    private final EvalService evalService;

    // ======================== Cases ========================

    @GetMapping("/cases")
    public Result<IPage<AiEvalCaseResponse>> listCases(@RequestParam(required = false) String category,
                                                       @RequestParam(required = false) Boolean activeOnly,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                evalService.pageCasesDetail(category, activeOnly, pageNum, pageSize),
                AiEvalCaseResponse.class));
    }

    @GetMapping("/cases/{id}")
    public Result<AiEvalCaseResponse> getCase(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convert(evalService.getCaseDetail(id), AiEvalCaseResponse.class));
    }

    @PostMapping("/cases")
    public Result<AiEvalCaseResponse> createCase(@Valid @RequestBody AiEvalCaseRequest request) {
        AiEvalCaseDTO dto = evalService.createCaseDetail(BeanConvertUtils.convert(request, AiEvalCaseDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiEvalCaseResponse.class));
    }

    @PutMapping("/cases/{id}")
    public Result<Void> updateCase(@PathVariable Long id, @Valid @RequestBody AiEvalCaseRequest request) {
        evalService.updateCaseDetail(id, BeanConvertUtils.convert(request, AiEvalCaseDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable Long id) {
        evalService.deleteCase(id);
        return Result.ok();
    }

    // ======================== Batch ========================

    @PostMapping("/batches")
    public Result<BatchResult> runBatch(@RequestParam(required = false) String category) {
        return Result.ok(evalService.runBatch(category));
    }

    @GetMapping("/batches/{batchId}")
    public Result<List<AiEvalRunResponse>> getBatch(@PathVariable String batchId) {
        return Result.ok(BeanConvertUtils.convertList(evalService.getBatchDetail(batchId), AiEvalRunResponse.class));
    }

    @GetMapping("/cases/{caseId}/runs")
    public Result<IPage<AiEvalRunResponse>> getCaseHistory(@PathVariable Long caseId,
                                                           @RequestParam(defaultValue = "1") int pageNum,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(BeanConvertUtils.convertPage(
                evalService.pageCaseHistoryDetail(caseId, pageNum, pageSize),
                AiEvalRunResponse.class));
    }
}
