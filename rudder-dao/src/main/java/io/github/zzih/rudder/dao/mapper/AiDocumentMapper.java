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

import io.github.zzih.rudder.dao.entity.AiDocument;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Mapper
public interface AiDocumentMapper extends BaseMapper<AiDocument> {

    List<AiDocument> selectByWorkspace(@Param("workspaceId") Long workspaceId,
                                       @Param("docType") String docType);

    IPage<AiDocument> selectPageByWorkspace(IPage<AiDocument> page,
                                            @Param("workspaceId") Long workspaceId,
                                            @Param("docType") String docType);

    /** 元信息分页(不含 content):给 list 浏览类调用,避免一页把全文 RAG 正文整页拉回。 */
    IPage<AiDocument> selectMetaPageByWorkspace(IPage<AiDocument> page,
                                                @Param("workspaceId") Long workspaceId,
                                                @Param("docType") String docType);

    AiDocument selectBySourceRef(@Param("sourceRef") String sourceRef);

    List<AiDocument> selectBySourceRefPrefix(@Param("prefix") String prefix);

    /** FULLTEXT 关键词召回(Qdrant 不可用时降级走这条)。 */
    List<AiDocument> fulltextSearch(@Param("q") String q,
                                    @Param("workspaceId") Long workspaceId,
                                    @Param("docType") String docType,
                                    @Param("engineTypes") Collection<String> engineTypes,
                                    @Param("limit") int limit);

    List<String> selectDistinctDocTypes(@Param("workspaceId") Long workspaceId);
}
