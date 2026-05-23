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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.DataPermRolePermissionDao;
import io.github.zzih.rudder.dao.entity.DataPermRolePermission;
import io.github.zzih.rudder.dao.entity.view.DataPermRolePermissionDetailView;
import io.github.zzih.rudder.dao.mapper.DataPermRolePermissionMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DataPermRolePermissionDaoImpl implements DataPermRolePermissionDao {

    private final DataPermRolePermissionMapper mapper;

    @Override
    public List<DataPermRolePermissionDetailView> selectByRoleId(Long roleId) {
        return mapper.queryByRoleId(roleId);
    }

    @Override
    public DataPermRolePermission selectByIdAndRole(Long id, Long roleId) {
        return mapper.selectByIdAndRole(id, roleId);
    }

    @Override
    public IPage<DataPermRolePermissionDetailView> selectPage(Long roleId, String keyword,
                                                              List<Long> scopeCodes,
                                                              int pageNum, int pageSize) {
        IPage<DataPermRolePermissionDetailView> page = new Page<>(pageNum, pageSize);
        return mapper.selectPageByRole(page, roleId, keyword, scopeCodes);
    }

    @Override
    public int insert(DataPermRolePermission item) {
        return mapper.insert(item);
    }

    @Override
    public int updateByIdAndRole(DataPermRolePermission item, Long roleId) {
        return mapper.updateByIdAndRole(item, roleId);
    }

    @Override
    public int deleteByIdAndRole(Long id, Long roleId) {
        return mapper.deleteByIdAndRole(id, roleId);
    }

    @Override
    public int deleteByRoleId(Long roleId) {
        return mapper.deleteByRoleId(roleId);
    }

    @Override
    public long countByScopeCode(Long scopeCode) {
        return mapper.countByScopeCode(scopeCode);
    }
}
