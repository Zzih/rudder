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

import io.github.zzih.rudder.ai.orchestrator.RagPipelineConfigService;
import io.github.zzih.rudder.ai.orchestrator.RagPipelineSettings;
import io.github.zzih.rudder.ai.rerank.RerankConfigService;
import io.github.zzih.rudder.api.request.SpiConfigRequest;
import io.github.zzih.rudder.api.request.SpiTestRequest;
import io.github.zzih.rudder.api.response.ProviderConfigResponse;
import io.github.zzih.rudder.api.response.RuntimeTypeResponse;
import io.github.zzih.rudder.api.security.annotation.RequireLoggedIn;
import io.github.zzih.rudder.api.security.annotation.RequireSuperAdmin;
import io.github.zzih.rudder.approval.api.plugin.ApprovalPluginManager;
import io.github.zzih.rudder.common.audit.AuditAction;
import io.github.zzih.rudder.common.audit.AuditLog;
import io.github.zzih.rudder.common.audit.AuditModule;
import io.github.zzih.rudder.common.audit.AuditResourceType;
import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.enums.RuntimeType;
import io.github.zzih.rudder.embedding.api.plugin.EmbeddingPluginManager;
import io.github.zzih.rudder.file.api.plugin.FilePluginManager;
import io.github.zzih.rudder.llm.api.plugin.LlmPluginManager;
import io.github.zzih.rudder.metadata.api.plugin.MetadataPluginManager;
import io.github.zzih.rudder.notification.api.plugin.NotificationPluginManager;
import io.github.zzih.rudder.publish.api.plugin.PublishPluginManager;
import io.github.zzih.rudder.rerank.api.plugin.RerankPluginManager;
import io.github.zzih.rudder.result.api.plugin.ResultPluginManager;
import io.github.zzih.rudder.runtime.api.plugin.RuntimePluginManager;
import io.github.zzih.rudder.service.config.ApprovalConfigService;
import io.github.zzih.rudder.service.config.EmbeddingConfigService;
import io.github.zzih.rudder.service.config.FileConfigService;
import io.github.zzih.rudder.service.config.LlmConfigService;
import io.github.zzih.rudder.service.config.MetadataConfigService;
import io.github.zzih.rudder.service.config.NotificationConfigService;
import io.github.zzih.rudder.service.config.PlatformConfigService;
import io.github.zzih.rudder.service.config.PublishConfigService;
import io.github.zzih.rudder.service.config.ResultConfigService;
import io.github.zzih.rudder.service.config.RuntimeConfigService;
import io.github.zzih.rudder.service.config.VectorConfigService;
import io.github.zzih.rudder.service.config.VersionConfigService;
import io.github.zzih.rudder.service.config.dto.ProviderConfigDTO;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.spi.api.model.PluginProviderDefinition;
import io.github.zzih.rudder.spi.api.model.TestResult;
import io.github.zzih.rudder.spi.api.model.ValidationResult;
import io.github.zzih.rudder.task.api.syntax.LanguageSyntax;
import io.github.zzih.rudder.task.api.syntax.SyntaxRegistry;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.task.api.task.enums.TaskTypeVO;
import io.github.zzih.rudder.vector.api.plugin.VectorPluginManager;
import io.github.zzih.rudder.version.api.plugin.VersionPluginManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final LlmPluginManager aiPluginManager;
    private final EmbeddingPluginManager embeddingPluginManager;
    private final VectorPluginManager vectorPluginManager;
    private final ApprovalPluginManager approvalPluginManager;
    private final FilePluginManager filePluginManager;
    private final FileConfigService fileConfigService;
    private final ResultConfigService resultConfigService;
    private final RuntimeConfigService runtimeConfigService;
    private final LlmConfigService llmConfigService;
    private final EmbeddingConfigService embeddingConfigService;
    private final VectorConfigService vectorConfigService;
    private final MetadataConfigService metadataConfigService;
    private final ApprovalConfigService approvalConfigService;
    private final VersionConfigService versionConfigService;
    private final ResultPluginManager resultPluginManager;
    private final MetadataPluginManager metadataPluginManager;
    private final NotificationPluginManager notificationPluginManager;
    private final VersionPluginManager versionPluginManager;
    private final RuntimePluginManager runtimePluginManager;
    private final RerankPluginManager rerankPluginManager;
    private final RerankConfigService rerankConfigService;
    private final RagPipelineConfigService ragPipelineConfigService;
    private final PublishPluginManager publishPluginManager;
    private final PublishConfigService publishConfigService;
    private final NotificationConfigService notificationConfigService;
    private final PlatformConfigService platformConfig;

    // ==================== 任务/运行时/语法 ====================

    @GetMapping("/task-types")
    @RequireLoggedIn
    public Result<List<TaskTypeVO>> taskTypes() {
        List<TaskTypeVO> list = Arrays.stream(TaskType.values())
                .map(TaskTypeVO::from)
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/runtime-types")
    @RequireLoggedIn
    public Result<List<RuntimeTypeResponse>> runtimeTypes() {
        List<RuntimeTypeResponse> list = Arrays.stream(RuntimeType.values())
                .map(rt -> new RuntimeTypeResponse(rt.getValue(), rt.getLabel()))
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/syntax/{taskType}")
    @RequireLoggedIn
    public Result<LanguageSyntax> syntax(@PathVariable TaskType taskType) {
        return Result.ok(SyntaxRegistry.get(taskType));
    }

    @GetMapping("/syntax")
    @RequireLoggedIn
    public Result<Map<String, LanguageSyntax>> allSyntax() {
        return Result.ok(SyntaxRegistry.getAll());
    }

    // ==================== 审批平台配置 ====================

    @GetMapping("/approval/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> approvalProviders() {
        return Result.ok(approvalPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/approval")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getApprovalConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveApproval(), ProviderConfigResponse.class));
    }

    @PostMapping("/approval")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.APPROVAL_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveApprovalConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(approvalPluginManager.providerKeys(), request.getProvider(), "approval provider");
        approvalConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== 元数据平台配置 ====================

    @GetMapping("/metadata/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> metadataProviders() {
        return Result.ok(metadataPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/metadata")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getMetadataConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveMetadata(), ProviderConfigResponse.class));
    }

    @PostMapping("/metadata")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.METADATA_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveMetadataConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(metadataPluginManager.providerKeys(), request.getProvider(), "metadata provider");
        metadataConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== 发布平台配置 ====================

    @GetMapping("/publish/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> publishProviders() {
        return Result.ok(publishPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/publish")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getPublishConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActivePublish(), ProviderConfigResponse.class));
    }

    @PostMapping("/publish")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.PUBLISH, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> savePublishConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(publishPluginManager.providerKeys(), request.getProvider(), "publish provider");
        publishConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    @GetMapping("/publish/health")
    @RequireSuperAdmin
    public Result<HealthStatus> publishHealth() {
        return Result.ok(publishConfigService.health());
    }

    // ==================== AI LLM 平台配置 ====================

    @GetMapping("/ai-llm/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> aiLlmProviders() {
        return Result.ok(aiPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/ai-llm")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getAiLlmConfig() {
        return Result.ok(BeanConvertUtils.convert(platformConfig.getActiveLlm(), ProviderConfigResponse.class));
    }

    @PostMapping("/ai-llm")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveAiLlmConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(aiPluginManager.providerKeys(), request.getProvider(), "ai-llm provider");
        llmConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== AI Embedding 平台配置 ====================

    @GetMapping("/ai-embedding/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> aiEmbeddingProviders() {
        return Result.ok(embeddingPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/ai-embedding")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getAiEmbeddingConfig() {
        return Result.ok(BeanConvertUtils.convert(platformConfig.getActiveEmbedding(), ProviderConfigResponse.class));
    }

    @PostMapping("/ai-embedding")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveAiEmbeddingConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(embeddingPluginManager.providerKeys(), request.getProvider(), "ai-embedding provider");
        embeddingConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== AI Vector 平台配置 ====================

    @GetMapping("/ai-vector/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> aiVectorProviders() {
        return Result.ok(vectorPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/ai-vector")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getAiVectorConfig() {
        return Result.ok(BeanConvertUtils.convert(platformConfig.getActiveVector(), ProviderConfigResponse.class));
    }

    @PostMapping("/ai-vector")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveAiVectorConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(vectorPluginManager.providerKeys(), request.getProvider(), "ai-vector provider");
        vectorConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== AI Rerank 平台配置 ====================

    @GetMapping("/ai-rerank/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> aiRerankProviders() {
        return Result.ok(rerankPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/ai-rerank")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getAiRerankConfig() {
        return Result.ok(BeanConvertUtils.convert(rerankConfigService.getActiveDetail(), ProviderConfigResponse.class));
    }

    @PostMapping("/ai-rerank")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveAiRerankConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(rerankPluginManager.providerKeys(), request.getProvider(), "ai-rerank provider");
        rerankConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    @PostMapping("/ai-rerank/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试 Rerank provider 配置")
    public Result<TestResult> testAiRerank(@RequestBody SpiTestRequest req) {
        return Result.ok(rerankPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @PostMapping("/ai-embedding/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试 Embedding provider 配置")
    public Result<TestResult> testAiEmbedding(@RequestBody SpiTestRequest req) {
        return Result.ok(embeddingPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @PostMapping("/ai-vector/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试 Vector provider 配置")
    public Result<TestResult> testAiVector(@RequestBody SpiTestRequest req) {
        return Result.ok(vectorPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @PostMapping("/ai-llm/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试 LLM provider 配置")
    public Result<TestResult> testAiLlm(@RequestBody SpiTestRequest req) {
        return Result.ok(aiPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    // ==================== AI RAG Pipeline 链路配置(单例) ====================

    @GetMapping("/ai-rag-pipeline")
    @RequireSuperAdmin
    public Result<RagPipelineSettings> getAiRagPipelineConfig() {
        return Result.ok(ragPipelineConfigService.active());
    }

    @PostMapping("/ai-rag-pipeline")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveAiRagPipelineConfig(@Valid @RequestBody RagPipelineSettings request) {
        ragPipelineConfigService.saveDetail(request);
        return Result.ok();
    }

    // ==================== 文件存储平台配置 ====================

    @GetMapping("/file/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> fileProviders() {
        return Result.ok(filePluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/file")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getFileConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveFile(), ProviderConfigResponse.class));
    }

    @PostMapping("/file")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.FILE_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveFileConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(filePluginManager.providerKeys(), request.getProvider(), "file provider");
        fileConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== 结果格式平台配置 ====================

    @GetMapping("/result/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> resultProviders() {
        return Result.ok(resultPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/result")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getResultConfig() {
        return Result.ok(BeanConvertUtils.convert(
                resultConfigService.getActiveDetail(), ProviderConfigResponse.class));
    }

    @PostMapping("/result")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.FILE_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveResultConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(resultPluginManager.providerKeys(), request.getProvider(), "result provider");
        resultConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== 版本存储平台配置 ====================

    @GetMapping("/version/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> versionProviders() {
        return Result.ok(versionPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/version")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getVersionConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveVersion(), ProviderConfigResponse.class));
    }

    @PostMapping("/version")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.VERSION_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveVersionConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(versionPluginManager.providerKeys(), request.getProvider(), "version provider");
        versionConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== Runtime 平台配置 ====================

    @GetMapping("/runtime/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> runtimeProviders() {
        return Result.ok(runtimePluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/runtime")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getRuntimeConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveRuntime(), ProviderConfigResponse.class));
    }

    @PostMapping("/runtime")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.RUNTIME_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveRuntimeConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(runtimePluginManager.providerKeys(), request.getProvider(), "runtime provider");
        runtimeConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    // ==================== 通知平台配置 ====================

    @GetMapping("/notification/providers")
    @RequireSuperAdmin
    public Result<Map<String, PluginProviderDefinition>> notificationProviders() {
        return Result.ok(notificationPluginManager.getProviderDefinitions(LocaleContextHolder.getLocale()));
    }

    @GetMapping("/notification")
    @RequireSuperAdmin
    public Result<ProviderConfigResponse> getNotificationConfig() {
        return Result.ok(BeanConvertUtils.convert(
                platformConfig.getActiveNotification(), ProviderConfigResponse.class));
    }

    @PostMapping("/notification")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.NOTIFICATION_CONFIG, action = AuditAction.UPDATE, resourceType = AuditResourceType.SPI_CONFIG)
    public Result<Void> saveNotificationConfig(@Valid @RequestBody SpiConfigRequest request) {
        validateKnown(notificationPluginManager.providerKeys(), request.getProvider(), "notification provider");
        notificationConfigService.saveDetail(BeanConvertUtils.convert(request, ProviderConfigDTO.class));
        return Result.ok();
    }

    @GetMapping("/notification/health")
    @RequireSuperAdmin
    public Result<HealthStatus> notificationHealth() {
        return Result.ok(notificationConfigService.health());
    }

    // ==================== SPI 校验 / 测试连接 / 健康 ====================

    @PostMapping("/file/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.FILE_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验文件 provider 配置")
    public Result<ValidationResult> validateFile(@RequestBody SpiTestRequest req) {
        return Result.ok(filePluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/file/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.FILE_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试文件 provider 配置")
    public Result<TestResult> testFile(@RequestBody SpiTestRequest req) {
        return Result.ok(filePluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/file/health")
    @RequireSuperAdmin
    public Result<HealthStatus> fileHealth() {
        return Result.ok(fileConfigService.health());
    }

    @PostMapping("/ai/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验 AI provider 配置")
    public Result<ValidationResult> validateAi(@RequestBody SpiTestRequest req) {
        return Result.ok(aiPluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/ai/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.AI_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试 AI provider 配置")
    public Result<TestResult> testAi(@RequestBody SpiTestRequest req) {
        return Result.ok(aiPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/ai/health")
    @RequireSuperAdmin
    public Result<HealthStatus> aiHealth() {
        return Result.ok(llmConfigService.health());
    }

    @PostMapping("/version/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.VERSION_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验版本存储 provider 配置")
    public Result<ValidationResult> validateVersion(@RequestBody SpiTestRequest req) {
        return Result.ok(versionPluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/version/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.VERSION_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试版本存储 provider 配置")
    public Result<TestResult> testVersion(@RequestBody SpiTestRequest req) {
        return Result.ok(versionPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/version/health")
    @RequireSuperAdmin
    public Result<HealthStatus> versionHealth() {
        return Result.ok(versionConfigService.health());
    }

    @PostMapping("/approval/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.APPROVAL_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验审批渠道配置")
    public Result<ValidationResult> validateApproval(@RequestBody SpiTestRequest req) {
        return Result.ok(approvalPluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/approval/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.APPROVAL_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试审批渠道配置")
    public Result<TestResult> testApproval(@RequestBody SpiTestRequest req) {
        return Result.ok(approvalPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/approval/health")
    @RequireSuperAdmin
    public Result<HealthStatus> approvalHealth() {
        return Result.ok(approvalConfigService.health());
    }

    @PostMapping("/metadata/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.METADATA_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验元数据 provider 配置")
    public Result<ValidationResult> validateMetadata(@RequestBody SpiTestRequest req) {
        return Result.ok(metadataPluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/metadata/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.METADATA_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试元数据 provider 配置")
    public Result<TestResult> testMetadata(@RequestBody SpiTestRequest req) {
        return Result.ok(metadataPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/metadata/health")
    @RequireSuperAdmin
    public Result<HealthStatus> metadataHealth() {
        return Result.ok(metadataConfigService.health());
    }

    @PostMapping("/notification/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.NOTIFICATION_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验通知渠道配置")
    public Result<ValidationResult> validateNotification(@RequestBody SpiTestRequest req) {
        return Result.ok(notificationPluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/notification/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.NOTIFICATION_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试通知渠道配置")
    public Result<TestResult> testNotification(@RequestBody SpiTestRequest req) {
        return Result.ok(notificationPluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @PostMapping("/runtime/validate")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.RUNTIME_CONFIG, action = AuditAction.VALIDATE, resourceType = AuditResourceType.SPI_CONFIG, description = "校验运行时 provider 配置")
    public Result<ValidationResult> validateRuntime(@RequestBody SpiTestRequest req) {
        return Result.ok(runtimePluginManager.validate(req.provider(), req.providerParams()));
    }

    @PostMapping("/runtime/test")
    @RequireSuperAdmin
    @AuditLog(module = AuditModule.RUNTIME_CONFIG, action = AuditAction.TEST, resourceType = AuditResourceType.SPI_CONFIG, description = "测试运行时 provider 配置")
    public Result<TestResult> testRuntime(@RequestBody SpiTestRequest req) {
        return Result.ok(runtimePluginManager.testConnection(req.provider(), req.providerParams()));
    }

    @GetMapping("/approval/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listApprovalConfigs() {
        return Result.ok(approvalConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/metadata/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listMetadataConfigs() {
        return Result.ok(metadataConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/publish/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listPublishConfigs() {
        return Result.ok(publishConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/ai-llm/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listLlmConfigs() {
        return Result.ok(llmConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/ai-embedding/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listEmbeddingConfigs() {
        return Result.ok(embeddingConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/ai-vector/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listVectorConfigs() {
        return Result.ok(vectorConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/ai-rerank/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listRerankConfigs() {
        return Result.ok(rerankConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/file/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listFileConfigs() {
        return Result.ok(fileConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/result/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listResultConfigs() {
        return Result.ok(resultConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/version/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listVersionConfigs() {
        return Result.ok(versionConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/runtime/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listRuntimeConfigs() {
        return Result.ok(runtimeConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    @GetMapping("/notification/configs")
    @RequireSuperAdmin
    public Result<List<ProviderConfigResponse>> listNotificationConfigs() {
        return Result.ok(notificationConfigService.listAll().stream()
                .map(d -> BeanConvertUtils.convert(d, ProviderConfigResponse.class))
                .toList());
    }

    private static void validateKnown(Set<String> known, String key, String kind) {
        if (key == null || !known.contains(key.toUpperCase())) {
            throw new BizException(ConfigErrorCode.UNKNOWN_PROVIDER_KIND, kind, key);
        }
    }
}
