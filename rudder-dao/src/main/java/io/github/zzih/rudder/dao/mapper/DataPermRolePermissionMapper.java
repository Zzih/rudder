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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.DataPermRolePermission;
import io.github.zzih.rudder.dao.entity.view.DataPermRolePermissionDetailView;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermRolePermissionMapper extends BaseMapper<DataPermRolePermission> {

    /** 列表 / 分页 / 单查均走 JOIN scope 直出 view,service 端无需再装配 scopeName。 */
    List<DataPermRolePermissionDetailView> queryByRoleId(@Param("roleId") Long roleId);

    int deleteByRoleId(@Param("roleId") Long roleId);

    long countByScopeCode(@Param("scopeCode") Long scopeCode);

    IPage<DataPermRolePermissionDetailView> selectPageByRole(IPage<DataPermRolePermissionDetailView> page,
                                                             @Param("roleId") Long roleId,
                                                             @Param("keyword") String keyword,
                                                             @Param("scopeCodes") List<Long> scopeCodes);

    DataPermRolePermission selectByIdAndRole(@Param("id") Long id, @Param("roleId") Long roleId);

    int updateByIdAndRole(@Param("entity") DataPermRolePermission entity,
                          @Param("roleId") Long roleId);

    int deleteByIdAndRole(@Param("id") Long id, @Param("roleId") Long roleId);
}
