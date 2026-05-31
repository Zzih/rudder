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

import io.github.zzih.rudder.dao.entity.DataPermBundleStatement;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermBundleStatementMapper extends BaseMapper<DataPermBundleStatement> {

    List<DataPermBundleStatement> queryByBundleId(@Param("bundleId") Long bundleId);

    /** 分页查某 role 的块;keyword 模糊匹配作用域名 / 分组名 / 库表路径。 */
    IPage<DataPermBundleStatement> queryPageByBundle(IPage<DataPermBundleStatement> page,
                                                     @Param("bundleId") Long bundleId,
                                                     @Param("keyword") String keyword);

    int deleteByBundleId(@Param("bundleId") Long bundleId);

    long countByScopeCode(@Param("scopeCode") Long scopeCode);

    /** 引用某操作分组的权限包块数(删分组前校验)。 */
    long countByGroupId(@Param("groupId") Long groupId);
}
