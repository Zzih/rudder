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

import io.github.zzih.rudder.dao.entity.AiDocumentEmbedding;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AiDocumentEmbeddingMapper extends BaseMapper<AiDocumentEmbedding> {

    List<AiDocumentEmbedding> selectByDocument(@Param("documentId") Long documentId);

    List<AiDocumentEmbedding> selectByPointIds(@Param("pointIds") Collection<String> pointIds);

    int deleteByDocument(@Param("documentId") Long documentId);

    /** 孤儿 embedding:关联的 AiDocument 已 soft-delete 或物理删除。limit 防一次性拖太多。 */
    List<AiDocumentEmbedding> selectOrphanEmbeddings(@Param("limit") int limit);
}
