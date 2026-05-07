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

import io.github.zzih.rudder.api.request.RedactionRuleRequest;
import io.github.zzih.rudder.api.request.RedactionStrategyRequest;
import io.github.zzih.rudder.api.request.RedactionTestRequest;
import io.github.zzih.rudder.api.response.RedactionRuleResponse;
import io.github.zzih.rudder.api.response.RedactionStrategyResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.redaction.RedactionAdminService;
import io.github.zzih.rudder.service.redaction.dto.RedactionRuleDTO;
import io.github.zzih.rudder.service.redaction.dto.RedactionStrategyDTO;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 平台级脱敏规则 + 策略管理。 */
@RestController
@RequestMapping("/api/platform/redaction")
@RequiredArgsConstructor
@RequireRole(RoleType.SUPER_ADMIN)
public class RedactionController {

    private final RedactionAdminService adminService;

    // ======================== Rules ========================

    @GetMapping("/rules")
    public Result<List<RedactionRuleResponse>> listRules() {
        return Result.ok(BeanConvertUtils.convertList(adminService.listRulesDetail(), RedactionRuleResponse.class));
    }

    @PostMapping("/rules")
    public Result<RedactionRuleResponse> createRule(@Valid @RequestBody RedactionRuleRequest request) {
        RedactionRuleDTO dto = adminService.createRuleDetail(BeanConvertUtils.convert(request, RedactionRuleDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, RedactionRuleResponse.class));
    }

    @PutMapping("/rules/{id}")
    public Result<Void> updateRule(@PathVariable Long id, @Valid @RequestBody RedactionRuleRequest request) {
        adminService.updateRuleDetail(id, BeanConvertUtils.convert(request, RedactionRuleDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        adminService.deleteRule(id);
        return Result.ok();
    }

    // ======================== Strategies ========================

    @GetMapping("/strategies")
    public Result<List<RedactionStrategyResponse>> listStrategies() {
        return Result.ok(BeanConvertUtils.convertList(
                adminService.listStrategiesDetail(), RedactionStrategyResponse.class));
    }

    @PostMapping("/strategies")
    public Result<RedactionStrategyResponse> createStrategy(@Valid @RequestBody RedactionStrategyRequest request) {
        RedactionStrategyDTO dto = adminService.createStrategyDetail(
                BeanConvertUtils.convert(request, RedactionStrategyDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, RedactionStrategyResponse.class));
    }

    @PutMapping("/strategies/{id}")
    public Result<Void> updateStrategy(@PathVariable Long id, @Valid @RequestBody RedactionStrategyRequest request) {
        adminService.updateStrategyDetail(id, BeanConvertUtils.convert(request, RedactionStrategyDTO.class));
        return Result.ok();
    }

    @DeleteMapping("/strategies/{id}")
    public Result<Void> deleteStrategy(@PathVariable Long id) {
        adminService.deleteStrategy(id);
        return Result.ok();
    }

    // ======================== Test ========================

    /**
     * 实时测试策略 / 规则。body 形如:
     * <pre>{
     *   "strategy": { "executorType": "REGEX_REPLACE", "matchRegex": "^(\\d{3})\\d{4}(\\d{4})$", "replacement": "$1****$2" },
     *   "rulePattern": "1[3-9]\\d{9}",   // 可选,TEXT 规则填;COLUMN/TAG 留空
     *   "sample": "13800138000"
     * }</pre>
     */
    @PostMapping("/test")
    public Result<Map<String, String>> testApply(@RequestBody RedactionTestRequest request) {
        RedactionStrategyDTO strategyDto = request.getStrategy() == null ? null
                : BeanConvertUtils.convert(request.getStrategy(), RedactionStrategyDTO.class);
        String out = adminService.testApply(strategyDto, request.getRulePattern(), request.getSample());
        return Result.ok(Map.of("output", out == null ? "" : out));
    }
}
