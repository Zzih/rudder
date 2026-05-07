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

import io.github.zzih.rudder.dao.entity.AiDocumentEmbedding;

import java.util.Collection;
import java.util.List;

public interface AiDocumentEmbeddingDao {

    List<AiDocumentEmbedding> selectByDocument(Long documentId);

    List<AiDocumentEmbedding> selectByPointIds(Collection<String> pointIds);

    int insert(AiDocumentEmbedding entity);

    int insertBatch(List<AiDocumentEmbedding> entities);

    int deleteByDocument(Long documentId);

    /** 孤儿 embedding:关联的 AiDocument 已 soft-delete 或物理删除。limit 防一次拉太多。 */
    List<AiDocumentEmbedding> selectOrphanEmbeddings(int limit);

    /** 按主键批量删除 embedding 行。配合 sweep 清孤儿。 */
    int deleteByIds(Collection<Long> ids);
}
