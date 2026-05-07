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

import io.github.zzih.rudder.dao.entity.AiDocument;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface AiDocumentDao {

    AiDocument selectById(Long id);

    /** RAG 检索命中多 chunk 时一次拿齐 document 元数据,避免 N+1。 */
    List<AiDocument> selectByIds(Collection<Long> ids);

    List<AiDocument> selectByWorkspace(Long workspaceId, String docType);

    IPage<AiDocument> selectPage(Long workspaceId, String docType, int pageNum, int pageSize);

    /** 元信息分页(不含 content);浏览类接口用,避免一页把 RAG 全文整页拉回。 */
    IPage<AiDocument> selectMetaPage(Long workspaceId, String docType, int pageNum, int pageSize);

    AiDocument selectBySourceRef(String sourceRef);

    List<AiDocument> selectBySourceRefPrefix(String prefix);

    List<AiDocument> fulltextSearch(String q, Long workspaceId, String docType,
                                    Collection<String> engineTypes, int limit);

    /** DB 里当前实际存在的所有 docType(同时包含 workspace 专属 + 平台共享)。用于 docType 不指定时跨类型检索。 */
    List<String> listDistinctDocTypes(Long workspaceId);

    int insert(AiDocument entity);

    int updateById(AiDocument entity);

    int deleteById(Long id);
}
