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
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.DataPermBundleStatementDao;
import io.github.zzih.rudder.dao.entity.DataPermBundleStatement;
import io.github.zzih.rudder.dao.entity.DataPermBundleStatementResource;
import io.github.zzih.rudder.dao.entity.view.DataPermBundleStatementResourceView;
import io.github.zzih.rudder.service.dataperm.adapter.RangerAdapterRegistry;
import io.github.zzih.rudder.service.dataperm.adapter.RangerResourceAdapter;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermScopeDTO;
import io.github.zzih.rudder.service.dataperm.dto.DataPermStatementDTO;
import io.github.zzih.rudder.service.dataperm.dto.ResourcePathDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权限包内「作用域块」行级 CRUD。一个块 = 一个 scope + 一组操作分组 + 多条库表行(每层可多选)。
 * 多选不在此展开,reconciler 物化时才展开成单元组。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleStatementService {

    private final DataPermBundleStatementDao blockDao;
    private final DataPermConfigService configService;
    private final DataPermScopeAccessGroupService groupService;
    private final RangerAdapterRegistry adapterRegistry;

    public List<DataPermStatementDTO> listStatements(Long bundleId) {
        return listStatements(bundleId, groupService.allNames());
    }

    /** 调用方在循环多个 role 时复用一份分组名表,避免每次 {@code allNames()} 全表扫描。 */
    public List<DataPermStatementDTO> listStatements(Long bundleId, Map<Long, String> groupNameById) {
        List<DataPermBundleStatementResourceView> views = blockDao.selectResourceViewsByBundleIds(List.of(bundleId));
        return toStatements(views, groupNameById);
    }

    /** 块级分页(后端分页 + 后端搜索):管理端编辑与申请人预览共用。 */
    public IPage<DataPermStatementDTO> pageStatements(Long bundleId, String keyword, int pageNum, int pageSize) {
        IPage<DataPermBundleStatement> page = blockDao.pageStatements(bundleId, keyword, pageNum, pageSize);
        List<Long> statementIds = page.getRecords().stream().map(DataPermBundleStatement::getId).toList();
        List<DataPermBundleStatementResourceView> views = blockDao.selectResourceViewsByStatementIds(statementIds);
        List<DataPermStatementDTO> dtos = toStatements(views, groupService.allNames());
        return new Page<DataPermStatementDTO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(dtos);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addStatement(Long bundleId, DataPermStatementDTO dto) {
        Normalized n = normalize(dto);
        DataPermBundleStatement block = new DataPermBundleStatement();
        block.setBundleId(bundleId);
        block.setScopeCode(n.scopeCode());
        block.setGroupIds(DataPermStatementSupport.toGroupIdsJson(n.groupIds()));
        Long id = blockDao.insertStatement(block);
        blockDao.replaceResources(id, n.resources());
        log.info("Role block added: bundleId={}, statementId={}", bundleId, id);
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatement(Long bundleId, Long statementId, DataPermStatementDTO dto) {
        DataPermBundleStatement existing = blockDao.selectStatementById(statementId);
        if (existing == null || !Objects.equals(existing.getBundleId(), bundleId)) {
            throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING, "bundleStatement:" + statementId);
        }
        Normalized n = normalize(dto);
        DataPermBundleStatement block = new DataPermBundleStatement();
        block.setId(statementId);
        block.setBundleId(bundleId);
        block.setScopeCode(n.scopeCode());
        block.setGroupIds(DataPermStatementSupport.toGroupIdsJson(n.groupIds()));
        blockDao.updateStatement(block);
        blockDao.replaceResources(statementId, n.resources());
        log.info("Role block updated: bundleId={}, statementId={}", bundleId, statementId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteStatement(Long bundleId, Long statementId) {
        DataPermBundleStatement existing = blockDao.selectStatementById(statementId);
        if (existing == null || !Objects.equals(existing.getBundleId(), bundleId)) {
            throw new NotFoundException(DataPermErrorCode.RESOURCE_MISSING, "bundleStatement:" + statementId);
        }
        blockDao.deleteStatement(statementId);
        log.info("Role block deleted: bundleId={}, statementId={}", bundleId, statementId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteByBundleId(Long bundleId) {
        return blockDao.deleteByBundleId(bundleId);
    }

    private record Normalized(Long scopeCode, List<Long> groupIds, List<DataPermBundleStatementResource> resources) {
    }

    private Normalized normalize(DataPermStatementDTO dto) {
        if (dto == null || dto.getScopeCode() == null) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "scopeCode required");
        }
        DataPermScopeDTO scope = configService.findScope(dto.getScopeCode())
                .orElseThrow(() -> new BizException(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND, dto.getScopeCode()));
        List<ResourceLevel> hierarchy = adapterRegistry.find(scope.getPluginType())
                .map(RangerResourceAdapter::resourceHierarchy).orElse(List.of());
        List<Long> groupIds = groupService.validateGroups(dto.getScopeCode(), dto.getGroupIds());
        if (dto.getResources() == null || dto.getResources().isEmpty()) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "block.resources required");
        }
        List<DataPermBundleStatementResource> entities = new ArrayList<>(dto.getResources().size());
        for (ResourcePathDTO r : dto.getResources()) {
            List<String> c = DataPermStatementSupport.normLevel(r.getCatalogNames());
            List<String> d = DataPermStatementSupport.normLevel(r.getDatabaseNames());
            List<String> t = DataPermStatementSupport.normLevel(r.getTableNames());
            List<String> col = DataPermStatementSupport.normLevel(r.getColumnNames());
            if (c.isEmpty() && d.isEmpty() && t.isEmpty() && col.isEmpty()) {
                throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "resource path empty");
            }
            List<List<String>> canon = DataPermStatementSupport.canonicalizeForcedAll(hierarchy, c, d, t, col);
            DataPermBundleStatementResource e = new DataPermBundleStatementResource();
            e.setCatalogNames(DataPermStatementSupport.toJson(canon.get(0)));
            e.setDatabaseNames(DataPermStatementSupport.toJson(canon.get(1)));
            e.setTableNames(DataPermStatementSupport.toJson(canon.get(2)));
            e.setColumnNames(DataPermStatementSupport.toJson(canon.get(3)));
            entities.add(e);
        }
        return new Normalized(dto.getScopeCode(), groupIds, entities);
    }

    static List<DataPermStatementDTO> toStatements(List<DataPermBundleStatementResourceView> views,
                                                   Map<Long, String> nameById) {
        Map<Long, DataPermStatementDTO> byBlock = new LinkedHashMap<>();
        for (DataPermBundleStatementResourceView v : views) {
            DataPermStatementDTO block = byBlock.computeIfAbsent(v.getStatementId(), k -> {
                DataPermStatementDTO dto = new DataPermStatementDTO();
                dto.setId(v.getStatementId());
                dto.setScopeCode(v.getScopeCode());
                dto.setScopeName(v.getScopeName());
                List<Long> gids = DataPermStatementSupport.parseGroupIds(v.getGroupIds());
                dto.setGroupIds(gids);
                dto.setGroupNames(DataPermScopeAccessGroupService.resolveNames(gids, nameById));
                dto.setResources(new ArrayList<>());
                return dto;
            });
            block.getResources().add(toResourcePath(v.getCatalogNames(), v.getDatabaseNames(),
                    v.getTableNames(), v.getColumnNames()));
        }
        return new ArrayList<>(byBlock.values());
    }

    static ResourcePathDTO toResourcePath(String catalogJson, String dbJson, String tableJson, String colJson) {
        ResourcePathDTO r = new ResourcePathDTO();
        r.setCatalogNames(DataPermStatementSupport.parse(catalogJson));
        r.setDatabaseNames(DataPermStatementSupport.parse(dbJson));
        r.setTableNames(DataPermStatementSupport.parse(tableJson));
        r.setColumnNames(DataPermStatementSupport.parse(colJson));
        return r;
    }
}
