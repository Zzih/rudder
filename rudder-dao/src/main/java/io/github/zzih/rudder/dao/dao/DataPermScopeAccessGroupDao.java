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

import io.github.zzih.rudder.dao.entity.DataPermScopeAccessGroup;

import java.util.Collection;
import java.util.List;

public interface DataPermScopeAccessGroupDao {

    /** 全表 scan,数量小(平台级几个 scope * 少量分组);reconciler 物化时一次性加载。 */
    List<DataPermScopeAccessGroup> selectAll();

    List<DataPermScopeAccessGroup> selectByScopeCode(Long scopeCode);

    /** 按 id 集批量查;空集返空。 */
    List<DataPermScopeAccessGroup> selectByIds(Collection<Long> ids);

    DataPermScopeAccessGroup selectById(Long id);

    int insert(DataPermScopeAccessGroup group);

    /** 显式更新 name/accesses/description(含置空),不走 updateById 的 null 跳过策略。 */
    int update(DataPermScopeAccessGroup group);

    int deleteById(Long id);

    int deleteByScopeCode(Long scopeCode);
}
