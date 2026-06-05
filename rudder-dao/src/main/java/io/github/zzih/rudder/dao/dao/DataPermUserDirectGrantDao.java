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

import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrant;
import io.github.zzih.rudder.dao.entity.DataPermUserDirectGrantResource;
import io.github.zzih.rudder.dao.entity.view.DataPermUserDirectGrantResourceView;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

/** 用户 direct 授权块 + 块内库表行的持久化。 */
public interface DataPermUserDirectGrantDao {

    DataPermUserDirectGrant selectById(Long id);

    /** 插入块,返回生成 id。 */
    Long insertStatement(DataPermUserDirectGrant block);

    void insertResources(Long statementId, List<DataPermUserDirectGrantResource> resources);

    List<DataPermUserDirectGrant> selectActiveByUser(Long userId, LocalDateTime asOf);

    List<DataPermUserDirectGrantResource> selectResourcesByStatementIds(Collection<Long> statementIds);

    /** 扁平库表行视图(resource + block + scope),reconciler 用;空集返空。 */
    List<DataPermUserDirectGrantResourceView> selectActiveResourceViewsByUserIds(
                                                                                 Collection<Long> userIds,
                                                                                 LocalDateTime asOf);

    /** 单个用户当前活跃直接授权的库表行经 JSON_TABLE 笛卡尔展开后的单元组分页(原生分页,total 为展开条数)。 */
    IPage<GrantItemRow> pageActiveFlattenedByUser(Long userId, LocalDateTime asOf, int pageNum, int pageSize);

    /** 单个用户当前活跃直接授权展开后的单元组总数(各层数组长度乘积之和)。 */
    long sumFlattenedActiveByUser(Long userId, LocalDateTime asOf);

    /** 按 id 批量拉 direct grant(历史分页补全用)。 */
    List<DataPermUserDirectGrant> selectByIds(Collection<Long> ids);

    List<DataPermUserDirectGrant> selectByApprovalId(Long approvalId);

    int expireIfActive(Long id, LocalDateTime now, String endReason, Long endBy, String endNote);

    List<Long> selectDistinctActiveUserIds(LocalDateTime asOf);

    long countByScopeCode(Long scopeCode);

    long countByGroupId(Long groupId);
}
