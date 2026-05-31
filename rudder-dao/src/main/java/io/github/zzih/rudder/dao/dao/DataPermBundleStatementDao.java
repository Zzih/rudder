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

import io.github.zzih.rudder.dao.entity.DataPermBundleStatement;
import io.github.zzih.rudder.dao.entity.DataPermBundleStatementResource;
import io.github.zzih.rudder.dao.entity.view.DataPermBundleStatementResourceView;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

/** 权限包作用域块 + 块内库表行的持久化(块与其资源一并管理)。 */
public interface DataPermBundleStatementDao {

    DataPermBundleStatement selectStatementById(Long id);

    /** 块级分页;keyword 模糊匹配作用域名 / 分组名 / 库表路径。 */
    IPage<DataPermBundleStatement> pageStatements(Long bundleId, String keyword, int pageNum, int pageSize);

    /** 扁平库表行视图(resource + block + scope),reconciler 与权限包展示共用;空集返空。 */
    List<DataPermBundleStatementResourceView> selectResourceViewsByBundleIds(Collection<Long> bundleIds);

    /** 同上,但按块 id 批量取(分页只取当前页块的库表行);空集返空。 */
    List<DataPermBundleStatementResourceView> selectResourceViewsByStatementIds(Collection<Long> statementIds);

    /** 单个权限包的库表行经 JSON_TABLE 笛卡尔展开后的单元组分页(原生分页,total 为展开条数)。 */
    IPage<GrantItemRow> pageFlattenedByBundle(Long bundleId, int pageNum, int pageSize);

    /** 插入块,返回生成 id。 */
    Long insertStatement(DataPermBundleStatement block);

    int updateStatement(DataPermBundleStatement block);

    /** 全量替换块内库表行(先删后插)。 */
    void replaceResources(Long statementId, List<DataPermBundleStatementResource> resources);

    /** 删块 + 其库表行。 */
    int deleteStatement(Long statementId);

    /** 删某 role 下全部块 + 库表行(权限包删除级联)。 */
    int deleteByBundleId(Long bundleId);

    long countByScopeCode(Long scopeCode);

    long countByGroupId(Long groupId);
}
