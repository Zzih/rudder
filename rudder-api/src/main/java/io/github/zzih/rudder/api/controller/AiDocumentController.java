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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.ai.dto.AiDocumentDTO;
import io.github.zzih.rudder.ai.rag.AiDocumentService;
import io.github.zzih.rudder.ai.rag.DocumentIngestionService;
import io.github.zzih.rudder.ai.rag.DocumentRetrievalService;
import io.github.zzih.rudder.ai.rag.DocumentRetrievalService.RetrievedChunk;
import io.github.zzih.rudder.api.request.AiDocumentRequest;
import io.github.zzih.rudder.api.response.AiDocumentResponse;
import io.github.zzih.rudder.api.security.annotation.RequireSuperAdmin;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AI 文档(RAG 语料)管理。仅 SUPER_ADMIN。 */
@RestController
@RequestMapping("/api/ai/documents")
@RequiredArgsConstructor
@RequireSuperAdmin
public class AiDocumentController {

    private final AiDocumentService aiDocumentService;
    private final DocumentIngestionService ingestionService;
    private final DocumentRetrievalService retrievalService;

    @GetMapping
    public Result<IPage<AiDocumentResponse>> list(
                                                  @RequestParam(required = false) String docType,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        Long workspaceId = UserContext.getWorkspaceId();
        return Result.ok(BeanConvertUtils.convertPage(
                aiDocumentService.pageDetail(workspaceId, docType, pageNum, pageSize),
                AiDocumentResponse.class));
    }

    @GetMapping("/{id}")
    public Result<AiDocumentResponse> get(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convert(
                aiDocumentService.getDetail(id), AiDocumentResponse.class));
    }

    @PostMapping
    public Result<AiDocumentResponse> create(@Valid @RequestBody AiDocumentRequest request) {
        AiDocumentDTO dto = aiDocumentService.ingestDetail(BeanConvertUtils.convert(request, AiDocumentDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, AiDocumentResponse.class));
    }

    @PutMapping("/{id}")
    public Result<AiDocumentResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody AiDocumentRequest request) {
        AiDocumentDTO dto = aiDocumentService.updateDetail(id,
                request.getTitle(), request.getContent(), request.getDescription());
        return Result.ok(BeanConvertUtils.convert(dto, AiDocumentResponse.class));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiDocumentService.delete(id);
        return Result.ok();
    }

    /**
     * 上传文件 → Tika 解析(PDF / DOCX / Markdown / HTML / TXT 等)→ 入库。
     * 用于业务知识 / 口径等外部文档批量导入。
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<AiDocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "WIKI") String docType,
                                             @RequestParam(required = false) String title,
                                             @RequestParam(required = false) String engineType,
                                             @RequestParam(required = false) List<Long> workspaceIds) throws Exception {
        Resource resource = new ByteArrayResource(file.getBytes()) {

            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> parsed = reader.read();
        StringBuilder content = new StringBuilder();
        for (Document d : parsed) {
            String text = d.getText();
            if (text != null && !text.isBlank()) {
                if (content.length() > 0) {
                    content.append("\n\n");
                }
                content.append(text);
            }
        }
        if (content.length() == 0) {
            throw new io.github.zzih.rudder.common.exception.BizException(
                    io.github.zzih.rudder.common.enums.error.AiErrorCode.DOCUMENT_FILE_EMPTY_AFTER_PARSE);
        }
        AiDocumentDTO dto = new AiDocumentDTO();
        dto.setWorkspaceIds(workspaceIds == null || workspaceIds.isEmpty()
                ? null
                : JsonUtils.toJson(workspaceIds));
        dto.setDocType(docType);
        dto.setEngineType(engineType);
        dto.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
        dto.setContent(content.toString());
        return Result.ok(BeanConvertUtils.convert(aiDocumentService.ingestDetail(dto), AiDocumentResponse.class));
    }

    /** 重建索引(换 embedding 模型后)。返回 success 数 + 失败 doc id 列表。 */
    @PostMapping("/reindex")
    public Result<DocumentIngestionService.ReindexReport> reindex(
                                                                  @RequestParam(required = false) String docType) {
        Long workspaceId = UserContext.getWorkspaceId();
        return Result.ok(ingestionService.reindexAll(workspaceId, docType));
    }

    /** 语义检索预览(admin 用来调教)。 */
    @GetMapping("/search")
    public Result<List<RetrievedChunk>> search(@RequestParam String q,
                                               @RequestParam(required = false) String docType,
                                               @RequestParam(defaultValue = "5") int topK) {
        Long workspaceId = UserContext.getWorkspaceId();
        return Result.ok(retrievalService.retrieve(workspaceId, docType, q, topK));
    }
}
