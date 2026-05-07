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

package io.github.zzih.rudder.ai.rag;

import io.github.zzih.rudder.ai.dto.AiDocumentDTO;
import io.github.zzih.rudder.common.enums.error.AiErrorCode;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiDocumentDao;
import io.github.zzih.rudder.dao.entity.AiDocument;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/**
 * AI 文档管理 service —— 把 controller 里的 dao 直连 / 可见性校验下沉到这里。
 * 真正的入库 / 重建索引委托给 {@link DocumentIngestionService}。
 */
@Service
@RequiredArgsConstructor
public class AiDocumentService {

    private final AiDocumentDao documentDao;
    private final DocumentIngestionService ingestionService;

    /**
     * 列表查询，admin 视角的"按 workspace 过滤"。workspaceId=null 时跨全平台返回所有文档。
     */
    public IPage<AiDocumentDTO> pageDetail(Long workspaceId, String docType, int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                documentDao.selectPage(workspaceId, docType, pageNum, pageSize),
                AiDocumentDTO.class);
    }

    public AiDocumentDTO getDetail(Long id) {
        return BeanConvertUtils.convert(loadOrThrow(id), AiDocumentDTO.class);
    }

    public AiDocumentDTO ingestDetail(AiDocumentDTO body) {
        AiDocument entity = BeanConvertUtils.convert(body, AiDocument.class);
        return BeanConvertUtils.convert(ingestionService.ingest(entity), AiDocumentDTO.class);
    }

    public AiDocumentDTO updateDetail(Long id, String title, String content, String description) {
        loadOrThrow(id);
        return BeanConvertUtils.convert(
                ingestionService.update(id, title, content, description),
                AiDocumentDTO.class);
    }

    public void delete(Long id) {
        loadOrThrow(id);
        ingestionService.delete(id);
    }

    private AiDocument loadOrThrow(Long id) {
        AiDocument doc = documentDao.selectById(id);
        if (doc == null) {
            throw new NotFoundException(AiErrorCode.DOCUMENT_NOT_FOUND, id);
        }
        return doc;
    }
}
