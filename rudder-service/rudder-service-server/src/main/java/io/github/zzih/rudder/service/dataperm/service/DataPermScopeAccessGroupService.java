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

package io.github.zzih.rudder.service.dataperm.service;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DataPermScopeAccessGroupDao;
import io.github.zzih.rudder.dao.entity.DataPermScopeAccessGroup;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeAccessGroupDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 操作分组只读查询 + 校验。分组的增删改随权限域一起走 {@code DataPermConfigService.saveDetail} 原子持久化,
 * 本类不写库(避免与 ConfigService 形成循环依赖)。
 */
@Service
@RequiredArgsConstructor
public class DataPermScopeAccessGroupService {

    private final DataPermScopeAccessGroupDao groupDao;

    public List<DataPermScopeAccessGroupDTO> listByScope(Long scopeCode) {
        return groupDao.selectByScopeCode(scopeCode).stream()
                .map(DataPermScopeAccessGroupService::toDto)
                .toList();
    }

    /** 校验 groupIds 全部存在且属于给定 scope;返回去重后的 id 列表。 */
    public List<Long> validateGroups(Long scopeCode, List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            throw new BizException(DataPermErrorCode.ACCESS_GROUP_REQUIRED);
        }
        List<Long> distinct = groupIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw new BizException(DataPermErrorCode.ACCESS_GROUP_REQUIRED);
        }
        List<DataPermScopeAccessGroup> found = groupDao.selectByIds(distinct);
        if (found.size() != distinct.size()) {
            throw new BizException(DataPermErrorCode.ACCESS_GROUP_NOT_FOUND, distinct);
        }
        for (DataPermScopeAccessGroup g : found) {
            if (!Objects.equals(g.getScopeCode(), scopeCode)) {
                throw new BizException(DataPermErrorCode.ACCESS_GROUP_SCOPE_MISMATCH, g.getId());
            }
        }
        return distinct;
    }

    /** 全量 id → name,用于权限项 / 授权视图批量回填分组展示名(分组总数小,一次性加载)。 */
    public Map<Long, String> allNames() {
        Map<Long, String> out = new HashMap<>();
        for (DataPermScopeAccessGroup g : groupDao.selectAll()) {
            out.put(g.getId(), g.getName());
        }
        return out;
    }

    /** 全量 id → 裸 access 列表,reconciler 物化时把 group 展开成 access(分组总数小,一次性加载)。 */
    public Map<Long, List<String>> allAccessesByGroupId() {
        Map<Long, List<String>> out = new HashMap<>();
        for (DataPermScopeAccessGroup g : groupDao.selectAll()) {
            List<String> accesses = JsonUtils.toList(g.getAccesses(), String.class);
            out.put(g.getId(), accesses == null ? List.of() : accesses);
        }
        return out;
    }

    /** groupIds → 展示名列表,未知 id 跳过;入参 null 返空。 */
    public static List<String> resolveNames(List<Long> groupIds, Map<Long, String> nameById) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(groupIds.size());
        for (Long id : groupIds) {
            String name = nameById.get(id);
            if (name != null) {
                out.add(name);
            }
        }
        return out;
    }

    public static DataPermScopeAccessGroupDTO toDto(DataPermScopeAccessGroup e) {
        DataPermScopeAccessGroupDTO dto = new DataPermScopeAccessGroupDTO();
        dto.setId(e.getId());
        dto.setScopeCode(e.getScopeCode());
        dto.setName(e.getName());
        dto.setAccesses(JsonUtils.toList(e.getAccesses(), String.class));
        dto.setDescription(e.getDescription());
        return dto;
    }
}
