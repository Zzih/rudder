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

package io.github.zzih.rudder.datasource.service;

import io.github.zzih.rudder.common.enums.error.DatasourceErrorCode;
import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.jdbc.JdbcConnections;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.DatasourceDao;
import io.github.zzih.rudder.dao.dao.DatasourcePermissionDao;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.dao.entity.DatasourcePermission;
import io.github.zzih.rudder.dao.enums.DatasourceType;
import io.github.zzih.rudder.datasource.dto.DatasourceDTO;
import io.github.zzih.rudder.datasource.model.DataSourceCredentials;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceDao datasourceDao;
    private final DatasourcePermissionDao datasourcePermissionDao;
    private final CredentialService credentialService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 创建新的数据源。加密凭证并检查名称唯一性。
     */
    public Datasource create(Datasource ds) {
        log.info("创建数据源, name={}, type={}", ds.getName(), ds.getDatasourceType());
        if (datasourceDao.countByName(ds.getName()) > 0) {
            log.warn("创建数据源失败: 名称已存在, name={}", ds.getName());
            throw new BizException(DatasourceErrorCode.DS_NAME_EXISTS);
        }

        // 如果存在凭证则加密
        if (ds.getCredential() != null) {
            DataSourceCredentials cred = JsonUtils.fromJson(ds.getCredential(), DataSourceCredentials.class);
            ds.setCredential(credentialService.encrypt(cred));
        }

        datasourceDao.insert(ds);
        return ds;
    }

    /**
     * 根据 ID 获取数据源。未找到则抛出 NotFoundException。
     */
    public Datasource getById(Long id) {
        Datasource ds = datasourceDao.selectById(id);
        if (ds == null) {
            throw new NotFoundException(DatasourceErrorCode.DS_NOT_FOUND, id);
        }
        return ds;
    }

    public Datasource getByIdWithWorkspace(Long workspaceId, Long id) {
        Datasource ds = getById(id);
        if (datasourcePermissionDao.countByDatasourceIdAndWorkspaceId(id, workspaceId) <= 0) {
            throw new BizException(SystemErrorCode.FORBIDDEN,
                    "Datasource not accessible from current workspace: " + id);
        }
        return ds;
    }

    /**
     * 列出所有数据源。仅供 SUPER_ADMIN 使用。
     */
    public List<Datasource> listAll() {
        return datasourceDao.selectAll();
    }

    /**
     * 通过权限表列出指定工作空间可访问的数据源。
     */
    public List<Datasource> listByWorkspaceId(Long workspaceId) {
        List<DatasourcePermission> permissions = datasourcePermissionDao.selectByWorkspaceId(workspaceId);

        if (permissions.isEmpty()) {
            return List.of();
        }

        List<Long> datasourceIds = permissions.stream()
                .map(DatasourcePermission::getDatasourceId)
                .collect(Collectors.toList());

        return datasourceDao.selectByIds(datasourceIds);
    }

    /**
     * 按类型列出工作空间可访问的数据源。
     */
    public List<Datasource> listByTypeAndWorkspace(String type, Long workspaceId) {
        List<Datasource> datasources = listByWorkspaceId(workspaceId);
        return datasources.stream()
                .filter(ds -> ds.getDatasourceType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    /**
     * 更新数据源。如果凭证发生变更则重新加密。
     */
    public Datasource update(Long id, Datasource ds) {
        log.info("更新数据源, id={}, name={}", id, ds.getName());
        Datasource existing = getById(id);

        // name 是 DataHub URN 的组成部分,创建后不允许修改,以保证元数据视图稳定。
        if (ds.getName() != null && !existing.getName().equals(ds.getName())) {
            throw new BizException(DatasourceErrorCode.DS_NAME_IMMUTABLE);
        }
        ds.setName(existing.getName());

        // 如果凭证变更则重新加密
        if (ds.getCredential() != null) {
            DataSourceCredentials cred = JsonUtils.fromJson(ds.getCredential(), DataSourceCredentials.class);
            ds.setCredential(credentialService.encrypt(cred));
        } else {
            ds.setCredential(existing.getCredential());
        }

        ds.setId(id);
        datasourceDao.updateById(ds);
        eventPublisher.publishEvent(new DatasourceChangedEvent(id));
        return ds;
    }

    /**
     * 根据 ID 删除数据源。
     */
    public void delete(Long id) {
        log.info("删除数据源, id={}", id);
        getById(id); // 确保存在
        datasourceDao.deleteById(id);
        eventPublisher.publishEvent(new DatasourceChangedEvent(id));
    }

    /**
     * 测试数据源的连通性。
     */
    public boolean testConnection(Long id) {
        DataSourceInfo info = getDataSourceInfo(id);
        DatasourceType dsType = DatasourceType.of(info.getType());
        try {
            return JdbcConnections.runWith(info.getJdbcUrl(), info.getUsername(), info.getPassword(),
                    info.getDriverClass(), conn -> {
                        // Flink JDBC 驱动不支持 isValid(),用 SELECT 1 代替
                        if (dsType == DatasourceType.FLINK) {
                            try (var stmt = conn.createStatement()) {
                                stmt.execute("SELECT 1");
                            }
                            return true;
                        }
                        return conn.isValid(5);
                    });
        } catch (Exception e) {
            log.error("Connection test failed for datasource {}: {}", id, e.getMessage());
            throw new BizException(DatasourceErrorCode.DS_CONNECTION_FAILED, e.getMessage(), e);
        }
    }

    /**
     * 根据名称获取数据源 ID。返回第一个匹配项。
     */
    public Long getIdByName(String name) {
        return getByName(name).getId();
    }

    /**
     * 根据名称获取数据源实体。返回第一个匹配项。
     */
    public Datasource getByName(String name) {
        Datasource ds = datasourceDao.selectOneByName(name);
        if (ds == null) {
            throw new NotFoundException(DatasourceErrorCode.DS_NOT_FOUND, name);
        }
        return ds;
    }

    /** 带解密凭证的 DataSourceInfo,name / hasCatalog / driverClass 等派生字段一次性填齐。 */
    public DataSourceInfo getDataSourceInfo(Long id) {
        return buildInfo(getById(id));
    }

    /** 按名称单跳:给 metadata sync / 跨 SPI 调用方避免 name→id→info 两次 DB 查询。 */
    public DataSourceInfo getDataSourceInfoByName(String name) {
        return buildInfo(getByName(name));
    }

    private DataSourceInfo buildInfo(Datasource ds) {
        DatasourceType dsType = DatasourceType.of(ds.getDatasourceType());
        DataSourceCredentials cred = ds.getCredential() != null && !ds.getCredential().isBlank()
                ? credentialService.decrypt(ds.getCredential())
                : null;
        return DataSourceInfo.builder()
                .name(ds.getName())
                .type(ds.getDatasourceType())
                .hasCatalog(dsType.isHasCatalog())
                .jdbcUrl(dsType.buildJdbcUrl(ds.getHost(), ds.getPort(), ds.getDefaultPath()))
                .username(cred != null ? cred.getUsername() : null)
                .password(cred != null ? cred.getPassword() : null)
                .driverClass(dsType.getDriverClassName())
                .properties(JsonUtils.toMap(ds.getParams()))
                .build();
    }

    // ==================== DTO-returning methods for Controller ====================

    public DatasourceDTO createDetail(Datasource ds) {
        return BeanConvertUtils.convert(create(ds), DatasourceDTO.class);
    }

    /** Controller 入口:DTO + credential 字符串 → entity → create → 返回 DTO。 */
    public DatasourceDTO createDetail(DatasourceDTO body, String credentialJson) {
        Datasource ds = BeanConvertUtils.convert(body, Datasource.class);
        if (credentialJson != null) {
            ds.setCredential(credentialJson);
        }
        return BeanConvertUtils.convert(create(ds), DatasourceDTO.class);
    }

    public DatasourceDTO updateDetail(Long id, DatasourceDTO body, String credentialJson) {
        Datasource ds = BeanConvertUtils.convert(body, Datasource.class);
        if (credentialJson != null) {
            ds.setCredential(credentialJson);
        }
        return BeanConvertUtils.convert(update(id, ds), DatasourceDTO.class);
    }

    /** Controller 用:按 (workspaceId/null, id) 取 DS 的 name,内部做权限校验。 */
    public String resolveNameByWorkspace(Long workspaceId, Long id) {
        Datasource ds = workspaceId != null ? getByIdWithWorkspace(workspaceId, id) : getById(id);
        return ds.getName();
    }

    public DatasourceDTO getByIdDetail(Long id) {
        return BeanConvertUtils.convert(getById(id), DatasourceDTO.class);
    }

    public DatasourceDTO getByIdWithWorkspaceDetail(Long workspaceId, Long id) {
        return BeanConvertUtils.convert(getByIdWithWorkspace(workspaceId, id), DatasourceDTO.class);
    }

    public List<DatasourceDTO> listAllDetail() {
        return BeanConvertUtils.convertList(listAll(), DatasourceDTO.class);
    }

    public List<DatasourceDTO> listByWorkspaceIdDetail(Long workspaceId) {
        return BeanConvertUtils.convertList(listByWorkspaceId(workspaceId), DatasourceDTO.class);
    }

    public DatasourceDTO updateDetail(Long id, Datasource ds) {
        return BeanConvertUtils.convert(update(id, ds), DatasourceDTO.class);
    }

}
