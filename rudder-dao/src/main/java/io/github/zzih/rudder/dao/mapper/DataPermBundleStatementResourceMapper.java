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

import io.github.zzih.rudder.dao.entity.DataPermBundleStatementResource;
import io.github.zzih.rudder.dao.entity.view.DataPermBundleStatementResourceView;
import io.github.zzih.rudder.dao.projection.GrantItemRow;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface DataPermBundleStatementResourceMapper extends BaseMapper<DataPermBundleStatementResource> {

    int deleteByStatementId(@Param("statementId") Long statementId);

    /** 扁平视图(resource + block + scope),按 role 批量取,reconciler + 权限包展示共用。 */
    List<DataPermBundleStatementResourceView> queryViewsByBundleIds(@Param("bundleIds") Collection<Long> bundleIds);

    /** 扁平视图,按块 id 批量取(分页时只取当前页块的库表行)。 */
    List<DataPermBundleStatementResourceView> queryViewsByStatementIds(@Param("statementIds") Collection<Long> statementIds);

    /** 单个权限包的库表行经 JSON_TABLE 笛卡尔展开后的单元组分页(LIMIT 由 page 注入)。 */
    List<GrantItemRow> queryPageFlattenedByBundle(IPage<?> page, @Param("bundleId") Long bundleId);

    /** 单个权限包展开后的单元组总数(各层数组长度乘积之和,空层计 1)。 */
    long sumFlattenedByBundle(@Param("bundleId") Long bundleId);
}
