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

import io.github.zzih.rudder.dao.entity.DataPermScopeAccessGroup;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DataPermScopeAccessGroupMapper extends BaseMapper<DataPermScopeAccessGroup> {

    List<DataPermScopeAccessGroup> queryAll();

    List<DataPermScopeAccessGroup> queryByScopeCode(@Param("scopeCode") Long scopeCode);

    /** 显式更新可编辑列(含置空 description),不走 BaseMapper.updateById 的 null 跳过策略。 */
    int updateGroup(DataPermScopeAccessGroup group);

    int deleteByScopeCode(@Param("scopeCode") Long scopeCode);
}
