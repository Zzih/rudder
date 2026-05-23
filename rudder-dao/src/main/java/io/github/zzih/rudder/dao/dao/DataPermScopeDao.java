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

import io.github.zzih.rudder.dao.entity.DataPermScope;

import java.util.List;

public interface DataPermScopeDao {

    /** 全表 scan,数量小(平台级几个 scope)。 */
    List<DataPermScope> selectAll();

    /** 按业务码查;不存在返 null。 */
    DataPermScope selectByCode(Long code);

    /** 按 TaskType 反查 scope(JSON_CONTAINS managed_task_types);未命中返 null。同一 TaskType 跨 scope 全局唯一,最多一条。 */
    DataPermScope selectByTaskType(String taskType);

    int insert(DataPermScope scope);

    int updateById(DataPermScope scope);

    int deleteByCode(Long code);
}
