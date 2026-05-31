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

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DataPermUserDirectGrantMapper extends BaseMapper<DataPermUserDirectGrant> {

    List<DataPermUserDirectGrant> queryActiveByUser(@Param("userId") Long userId,
                                                    @Param("asOf") LocalDateTime asOf);

    List<DataPermUserDirectGrant> queryByApprovalId(@Param("approvalId") Long approvalId);

    List<DataPermUserDirectGrant> queryInactiveByUser(@Param("userId") Long userId,
                                                      @Param("now") LocalDateTime now);

    int expireIfActive(@Param("id") Long id,
                       @Param("now") LocalDateTime now,
                       @Param("endReason") String endReason,
                       @Param("endBy") Long endBy,
                       @Param("endNote") String endNote);

    List<Long> queryDistinctActiveUserIds(@Param("asOf") LocalDateTime asOf);

    long countByScopeCode(@Param("scopeCode") Long scopeCode);

    /** 引用某操作分组的 active direct 块数(删分组前校验)。 */
    long countByGroupId(@Param("groupId") Long groupId);
}
