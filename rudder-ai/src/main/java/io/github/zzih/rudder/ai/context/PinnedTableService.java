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

package io.github.zzih.rudder.ai.context;

import io.github.zzih.rudder.ai.dto.AiPinnedTableDTO;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiPinnedTableDao;
import io.github.zzih.rudder.dao.entity.AiPinnedTable;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** pinned 表管理。前端用 "db.table" 字符串交互,scope=USER 时 scopeId=userId。 */
@Service
@RequiredArgsConstructor
public class PinnedTableService {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_WORKSPACE = "WORKSPACE";

    private final AiPinnedTableDao dao;

    public List<AiPinnedTable> list(String scope, Long scopeId) {
        return dao.selectByScope(scope, scopeId);
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<AiPinnedTable> page(
                                                                            String scope, Long scopeId, int pageNum,
                                                                            int pageSize) {
        return dao.selectPageByScope(scope, scopeId, pageNum, pageSize);
    }

    /** 幂等:已存在直接返回原记录。 */
    public AiPinnedTable pin(String scope, Long scopeId, Long datasourceId, String database, String table,
                             String note) {
        AiPinnedTable existing = dao.selectOne(scope, scopeId, datasourceId, database, table);
        if (existing != null) {
            return existing;
        }
        AiPinnedTable e = new AiPinnedTable();
        e.setScope(scope);
        e.setScopeId(scopeId);
        e.setDatasourceId(datasourceId);
        e.setDatabaseName(database);
        e.setTableName(table);
        e.setNote(note);
        dao.insert(e);
        return e;
    }

    public void unpin(String scope, Long scopeId, Long datasourceId, String database, String table) {
        dao.deleteByScopeAndRef(scope, scopeId, datasourceId, database, table);
    }

    public void unpinById(Long id) {
        dao.deleteById(id);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public com.baomidou.mybatisplus.core.metadata.IPage<AiPinnedTableDTO> pageDetail(
                                                                                     String scope, Long scopeId,
                                                                                     int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(page(scope, scopeId, pageNum, pageSize), AiPinnedTableDTO.class);
    }

    public AiPinnedTableDTO pinDetail(String scope, Long scopeId, Long datasourceId, String database, String table,
                                      String note) {
        return BeanConvertUtils.convert(pin(scope, scopeId, datasourceId, database, table, note),
                AiPinnedTableDTO.class);
    }

    /** 给 ContextBuilder 消费:序列化为 ["db.table", ...] 列表(Context 里不需要 datasource 冗余)。 */
    public List<String> listRefs(String scope, Long scopeId) {
        List<AiPinnedTable> rows = list(scope, scopeId);
        List<String> refs = new ArrayList<>(rows.size());
        for (AiPinnedTable r : rows) {
            String db = r.getDatabaseName() == null ? "" : r.getDatabaseName();
            refs.add(db.isEmpty() ? r.getTableName() : db + "." + r.getTableName());
        }
        return refs;
    }
}
