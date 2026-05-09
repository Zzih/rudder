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

package io.github.zzih.rudder.service.config;

import io.github.zzih.rudder.approval.api.plugin.ApprovalPluginManager;
import io.github.zzih.rudder.dao.dao.SpiConfigDao;
import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;
import io.github.zzih.rudder.embedding.api.plugin.EmbeddingPluginManager;
import io.github.zzih.rudder.file.api.plugin.FilePluginManager;
import io.github.zzih.rudder.llm.api.plugin.LlmPluginManager;
import io.github.zzih.rudder.metadata.api.plugin.MetadataPluginManager;
import io.github.zzih.rudder.notification.api.plugin.NotificationPluginManager;
import io.github.zzih.rudder.publish.api.plugin.PublishPluginManager;
import io.github.zzih.rudder.result.api.plugin.ResultPluginManager;
import io.github.zzih.rudder.runtime.api.plugin.RuntimePluginManager;
import io.github.zzih.rudder.service.config.dto.ProviderConfigDTO;
import io.github.zzih.rudder.spi.api.AbstractConfigurablePluginRegistry;
import io.github.zzih.rudder.vector.api.plugin.VectorPluginManager;
import io.github.zzih.rudder.version.api.plugin.VersionPluginManager;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller 用的 config 聚合读服务:从 {@link SpiConfigDao} 按 {@link SpiType} 拉 active SpiConfig 行,
 * 然后用对应 SPI 的 PluginManager 把 {@code providerParams} JSON 反序列化为结构化对象,塞 DTO 返回。
 *
 * <p>所有 12 个 SPI 走同一套 {@link #buildDto} 模板,返回的 {@code providerParams} 已经是 Properties POJO,
 * Jackson 在 Controller 序列化 Response 时按字段输出,前端拿到结构化对象。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformConfigService {

    private final SpiConfigDao spiConfigDao;
    private final FilePluginManager filePluginManager;
    private final ResultPluginManager resultPluginManager;
    private final RuntimePluginManager runtimePluginManager;
    private final MetadataPluginManager metadataPluginManager;
    private final VersionPluginManager versionPluginManager;
    private final ApprovalPluginManager approvalPluginManager;
    private final PublishPluginManager publishPluginManager;
    private final NotificationPluginManager notificationPluginManager;
    private final LlmPluginManager llmPluginManager;
    private final EmbeddingPluginManager embeddingPluginManager;
    private final VectorPluginManager vectorPluginManager;

    public ProviderConfigDTO getActiveFile() {
        return buildDto(SpiType.FILE, filePluginManager);
    }

    public ProviderConfigDTO getActiveResult() {
        return buildDto(SpiType.RESULT, resultPluginManager);
    }

    public ProviderConfigDTO getActiveRuntime() {
        return buildDto(SpiType.RUNTIME, runtimePluginManager);
    }

    public ProviderConfigDTO getActiveMetadata() {
        return buildDto(SpiType.METADATA, metadataPluginManager);
    }

    public ProviderConfigDTO getActiveVersion() {
        return buildDto(SpiType.VERSION, versionPluginManager);
    }

    public ProviderConfigDTO getActiveApproval() {
        return buildDto(SpiType.APPROVAL, approvalPluginManager);
    }

    public ProviderConfigDTO getActivePublish() {
        return buildDto(SpiType.PUBLISH, publishPluginManager);
    }

    public ProviderConfigDTO getActiveNotification() {
        return buildDto(SpiType.NOTIFICATION, notificationPluginManager);
    }

    public ProviderConfigDTO getActiveLlm() {
        return buildDto(SpiType.LLM, llmPluginManager);
    }

    public ProviderConfigDTO getActiveEmbedding() {
        return buildDto(SpiType.EMBEDDING, embeddingPluginManager);
    }

    public ProviderConfigDTO getActiveVector() {
        return buildDto(SpiType.VECTOR, vectorPluginManager);
    }

    private ProviderConfigDTO buildDto(SpiType type, AbstractConfigurablePluginRegistry<?, ?> pm) {
        SpiConfig c = spiConfigDao.selectActive(type);
        if (c == null) {
            return null;
        }
        ProviderConfigDTO dto = new ProviderConfigDTO();
        dto.setId(c.getId());
        dto.setProvider(c.getProvider());
        dto.setEnabled(c.getEnabled());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        try {
            dto.setProviderParams(pm.deserialize(c.getProvider(), c.getProviderParams()));
        } catch (Exception e) {
            log.warn("deserialize {} provider={} failed, return raw row without params", type, c.getProvider(), e);
        }
        return dto;
    }
}
