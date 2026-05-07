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

package io.github.zzih.rudder.service.redaction;

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.RedactionRuleDao;
import io.github.zzih.rudder.dao.dao.RedactionStrategyDao;
import io.github.zzih.rudder.dao.entity.RedactionRuleEntity;
import io.github.zzih.rudder.dao.entity.RedactionStrategyEntity;
import io.github.zzih.rudder.service.redaction.dto.RedactionRuleDTO;
import io.github.zzih.rudder.service.redaction.dto.RedactionStrategyDTO;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 脱敏规则 + 策略的 admin CRUD 聚合。规则 / 策略任一变更都通过
 * {@link RedactionRuleCache#invalidate} 广播,所有节点立即 refresh 本地 snapshot。
 */
@Service
@RequiredArgsConstructor
public class RedactionAdminService {

    private final RedactionRuleDao ruleDao;
    private final RedactionStrategyDao strategyDao;
    private final RedactionService redactionService;
    private final RedactionRuleCache redactionRuleCache;

    // ==================== rules ====================

    public List<RedactionRuleEntity> listRules() {
        return ruleDao.selectAll();
    }

    public RedactionRuleEntity createRule(RedactionRuleEntity body) {
        ruleDao.insert(body);
        redactionRuleCache.invalidate();
        return body;
    }

    public void updateRule(Long id, RedactionRuleEntity body) {
        body.setId(id);
        ruleDao.updateById(body);
        redactionRuleCache.invalidate();
    }

    public void deleteRule(Long id) {
        ruleDao.deleteById(id);
        redactionRuleCache.invalidate();
    }

    // ==================== strategies ====================

    public List<RedactionStrategyEntity> listStrategies() {
        return strategyDao.selectAll();
    }

    public RedactionStrategyEntity createStrategy(RedactionStrategyEntity body) {
        strategyDao.insert(body);
        redactionRuleCache.invalidate();
        return body;
    }

    public void updateStrategy(Long id, RedactionStrategyEntity body) {
        body.setId(id);
        // code 是规则引用键,改名等同于让所有引用悬空——锁定不可改。
        RedactionStrategyEntity existing = strategyDao.selectById(id);
        if (existing != null) {
            body.setCode(existing.getCode());
        }
        strategyDao.updateById(body);
        redactionRuleCache.invalidate();
    }

    public void deleteStrategy(Long id) {
        RedactionStrategyEntity existing = strategyDao.selectById(id);
        if (existing == null) {
            return;
        }
        long refs = ruleDao.countByStrategyCode(existing.getCode());
        if (refs > 0) {
            throw new IllegalStateException(
                    "strategy " + existing.getCode() + " is referenced by " + refs + " rule(s); detach first");
        }
        strategyDao.deleteById(id);
        redactionRuleCache.invalidate();
    }

    // ==================== test ====================

    /** 试跑策略 / 规则 —— 不落库,委托给 {@link RedactionService#testApply}。 */
    public String testApply(RedactionStrategyDTO strategy, String rulePattern, String sample) {
        if (strategy == null) {
            return sample;
        }
        StrategySpec spec = StrategySpec.builder()
                .executorType(strategy.getExecutorType())
                .matchRegex(strategy.getMatchRegex())
                .replacement(strategy.getReplacement())
                .keepPrefix(strategy.getKeepPrefix())
                .keepSuffix(strategy.getKeepSuffix())
                .maskChar(strategy.getMaskChar())
                .replaceValue(strategy.getReplaceValue())
                .hashLength(strategy.getHashLength())
                .build();
        return redactionService.testApply(spec, rulePattern, sample);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public List<RedactionRuleDTO> listRulesDetail() {
        return BeanConvertUtils.convertList(listRules(), RedactionRuleDTO.class);
    }

    public RedactionRuleDTO createRuleDetail(RedactionRuleDTO body) {
        return BeanConvertUtils.convert(
                createRule(BeanConvertUtils.convert(body, RedactionRuleEntity.class)),
                RedactionRuleDTO.class);
    }

    public void updateRuleDetail(Long id, RedactionRuleDTO body) {
        updateRule(id, BeanConvertUtils.convert(body, RedactionRuleEntity.class));
    }

    public List<RedactionStrategyDTO> listStrategiesDetail() {
        return BeanConvertUtils.convertList(listStrategies(), RedactionStrategyDTO.class);
    }

    public RedactionStrategyDTO createStrategyDetail(RedactionStrategyDTO body) {
        return BeanConvertUtils.convert(
                createStrategy(BeanConvertUtils.convert(body, RedactionStrategyEntity.class)),
                RedactionStrategyDTO.class);
    }

    public void updateStrategyDetail(Long id, RedactionStrategyDTO body) {
        updateStrategy(id, BeanConvertUtils.convert(body, RedactionStrategyEntity.class));
    }
}
