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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.DataPermRolePermission;
import io.github.zzih.rudder.dao.entity.view.DataPermRolePermissionDetailView;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface DataPermRolePermissionDao {

    /** 列表 / 分页直出 detail view,scopeName join 在 DB 端拿。 */
    List<DataPermRolePermissionDetailView> selectByRoleId(Long roleId);

    DataPermRolePermission selectByIdAndRole(Long id, Long roleId);

    IPage<DataPermRolePermissionDetailView> selectPage(Long roleId, String keyword,
                                                       List<Long> scopeCodes,
                                                       int pageNum, int pageSize);

    int insert(DataPermRolePermission item);

    /** 单 SQL 更新 + role 归属校验;affected rows 为 0 表示 id 不存在或不属于该 role。 */
    int updateByIdAndRole(DataPermRolePermission item, Long roleId);

    /** 单 SQL 删除 + role 归属校验;affected rows 为 0 表示 id 不存在或不属于该 role。 */
    int deleteByIdAndRole(Long id, Long roleId);

    int deleteByRoleId(Long roleId);

    /** 计算引用某 Ranger Service 的 permission item 数(校验 service 是否仍被引用时用)。 */
    long countByScopeCode(Long scopeCode);
}
