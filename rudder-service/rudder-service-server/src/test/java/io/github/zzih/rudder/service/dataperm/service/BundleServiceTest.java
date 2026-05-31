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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.DataPermBundleDao;
import io.github.zzih.rudder.dao.dao.DataPermUserBundleGrantDao;
import io.github.zzih.rudder.dao.entity.DataPermBundle;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;
import io.github.zzih.rudder.service.permission.WorkspacePermissionService;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BundleServiceTest {

    @Mock
    private DataPermBundleDao bundleDao;

    @Mock
    private BundleStatementService bundleStatementService;

    @Mock
    private DataPermUserBundleGrantDao userBundleGrantDao;

    @Mock
    private WorkspacePermissionService workspacePermissionService;

    @InjectMocks
    private BundleService service;

    private static DataPermBundle role(Long id, String name) {
        DataPermBundle r = new DataPermBundle();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private static DataPermPermissionItemDTO sampleItem() {
        return DataPermPermissionItemDTO.builder()
                .scopeCode(7L)
                .databaseName("ods")
                .tableName("orders")
                .accesses(List.of("select"))
                .build();
    }

    @Test
    @DisplayName("create: 名称重复 → 抛 ROLE_NAME_DUPLICATE")
    void createDuplicateNameThrows() {
        when(bundleDao.countByName("BI")).thenReturn(1L);
        assertThatThrownBy(() -> service.create("BI", null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.ROLE_NAME_DUPLICATE);
    }

    @Test
    @DisplayName("create: 名称空 → 抛 APPLICATION_INVALID")
    void createBlankNameThrows() {
        assertThatThrownBy(() -> service.create("   ", null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.APPLICATION_INVALID);
    }

    @Test
    @DisplayName("create: 成功 → 仅 INSERT role,权限项后续单独加")
    void createSuccess() {
        when(bundleDao.countByName("BI")).thenReturn(0L);

        Long id = service.create("BI", "desc");

        verify(bundleDao).insert(any(DataPermBundle.class));
        // role.getId() 在 mock 下不会被 MyBatis Plus 自动赋值, 这里只验证流程, id 可能为 null
        assertThat(id).isNull();
    }

    @Test
    @DisplayName("updateMeta: role 不存在 → NotFoundException")
    void updateMetaNotFound() {
        when(bundleDao.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateMeta(99L, "new", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("updateMeta: 改名为别人占用 → ROLE_NAME_DUPLICATE")
    void updateMetaDuplicateName() {
        when(bundleDao.selectById(7L)).thenReturn(role(7L, "old"));
        when(bundleDao.countByNameExcludeId(eq("new"), eq(7L))).thenReturn(1L);

        assertThatThrownBy(() -> service.updateMeta(7L, "new", "desc"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.ROLE_NAME_DUPLICATE);
    }

    @Test
    @DisplayName("updateMeta: 成功 → UPDATE,不动权限项")
    void updateMetaSuccess() {
        when(bundleDao.selectById(7L)).thenReturn(role(7L, "old"));
        when(bundleDao.countByNameExcludeId(eq("new"), eq(7L))).thenReturn(0L);

        service.updateMeta(7L, "new", "desc");

        verify(bundleDao).updateById(any(DataPermBundle.class));
    }

    @Test
    @DisplayName("delete: role 不存在 → NotFoundException")
    void deleteNotFound() {
        when(bundleDao.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("delete: 成功 → 级联 expire grants + 删 permission + 删 role,返回影响 grant 数")
    void deleteSuccess() {
        when(bundleDao.selectById(7L)).thenReturn(role(7L, "BI"));
        when(userBundleGrantDao.expireAllByBundleId(eq(7L), any(LocalDateTime.class), anyString()))
                .thenReturn(5);

        int affected = service.delete(7L);

        assertThat(affected).isEqualTo(5);
        verify(userBundleGrantDao).expireAllByBundleId(eq(7L), any(LocalDateTime.class), eq("ROLE_DELETED"));
        verify(bundleStatementService).deleteByBundleId(7L);
        verify(bundleDao).deleteById(7L);
    }

    @Test
    @DisplayName("get: 详情含 activeGrantCount(权限项走分页 endpoint,不再随 role 返回)")
    void getDetail() {
        when(bundleDao.selectById(7L)).thenReturn(role(7L, "BI"));
        when(userBundleGrantDao.countActiveByBundleId(eq(7L), any())).thenReturn(2L);

        var dto = service.get(7L);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("BI");
        assertThat(dto.getActiveGrantCount()).isEqualTo(2L);
    }
}
