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

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantResourceView;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermUserDirectGrantResourceMapper extends BaseMapper<DataPermUserDirectGrantResource> {

    List<DataPermUserDirectGrantResource> queryByStatementIds(@Param("statementIds") Collection<Long> statementIds);

    int deleteByStatementId(@Param("statementId") Long statementId);

    /** 扁平视图(resource + block + scope),按 user 批量取 active,reconciler 用。 */
    List<DataPermUserDirectGrantResourceView> queryActiveViewsByUserIds(
                                                                        @Param("userIds") Collection<Long> userIds,
                                                                        @Param("asOf") LocalDateTime asOf);

    /** 单个用户当前活跃直接授权的库表行经 JSON_TABLE 笛卡尔展开后的单元组分页(LIMIT 由 page 注入)。 */
    List<GrantItemRow> queryPageActiveFlattenedByUser(IPage<?> page, @Param("userId") Long userId,
                                                      @Param("asOf") LocalDateTime asOf);

    /** 单个用户当前活跃直接授权展开后的单元组总数(各层数组长度乘积之和,空层计 1)。 */
    long sumFlattenedActiveByUser(@Param("userId") Long userId, @Param("asOf") LocalDateTime asOf);
}
