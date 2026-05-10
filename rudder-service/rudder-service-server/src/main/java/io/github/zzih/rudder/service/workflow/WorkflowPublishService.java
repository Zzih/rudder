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

package io.github.zzih.rudder.service.workflow;

import static io.github.zzih.rudder.approval.api.model.ApprovalExtraKeys.INITIATOR_EMAIL;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;
import io.github.zzih.rudder.dao.dao.DatasourceDao;
import io.github.zzih.rudder.dao.dao.ProjectDao;
import io.github.zzih.rudder.dao.dao.PublishRecordDao;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.dao.TaskDefinitionDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.dao.WorkflowDefinitionDao;
import io.github.zzih.rudder.dao.entity.Datasource;
import io.github.zzih.rudder.dao.entity.Project;
import io.github.zzih.rudder.dao.entity.PublishRecord;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.entity.WorkflowDefinition;
import io.github.zzih.rudder.dao.entity.WorkflowSchedule;
import io.github.zzih.rudder.dao.enums.PublishStatus;
import io.github.zzih.rudder.dao.enums.PublishType;
import io.github.zzih.rudder.dao.projection.PublishBatchDetailRow;
import io.github.zzih.rudder.dao.projection.PublishBatchRow;
import io.github.zzih.rudder.datasource.service.CredentialService;
import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.notification.api.model.ApprovalSubmittedMessage;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.UserRef;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.bundle.DatasourceBundle;
import io.github.zzih.rudder.publish.api.bundle.EdgeBundle;
import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.ResourceBundle;
import io.github.zzih.rudder.publish.api.bundle.ScheduleBundle;
import io.github.zzih.rudder.publish.api.bundle.TaskBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;
import io.github.zzih.rudder.service.config.FileConfigService;
import io.github.zzih.rudder.service.config.PublishConfigService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.version.VersionService;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;
import io.github.zzih.rudder.service.workflow.dto.ApprovalRecordDTO;
import io.github.zzih.rudder.service.workflow.dto.PublishBatchDTO;
import io.github.zzih.rudder.service.workflow.dto.PublishRecordDTO;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowPublishService {

    static final String RESOURCE_TYPE_WORKFLOW_PUBLISH = "WORKFLOW_PUBLISH";

    private final PublishRecordDao publishRecordDao;
    private final WorkflowDefinitionDao workflowDefinitionDao;
    private final ProjectDao projectDao;
    private final TaskDefinitionDao taskDefinitionDao;
    private final ScriptDao scriptDao;
    private final DatasourceDao datasourceDao;
    private final CredentialService credentialService;
    private final FileConfigService fileConfigService;
    private final ApprovalService approvalService;
    private final WorkflowVersionService workflowVersionService;
    private final WorkflowScheduleService workflowScheduleService;
    private final VersionService versionService;
    private final NotificationService notificationService;
    private final UserDao userDao;
    private final PublishConfigService publishConfigService;

    /**
     * 按 batchCode 分页查询发布批次，每个批次包含关联的工作流明细。
     * DAO 返回已是类型化投影，无需 Map 强转。
     */
    public PageResult<PublishBatchDTO> pageBatches(Long projectCode, String status,
                                                   int pageNum, int pageSize) {
        IPage<PublishBatchRow> batchPage =
                publishRecordDao.selectBatchPage(projectCode, status, pageNum, pageSize);
        List<PublishBatchRow> batchRows = batchPage.getRecords();
        if (batchRows.isEmpty()) {
            return PageResult.of(List.of(), 0L, pageNum, pageSize);
        }

        List<Long> batchCodes = batchRows.stream().map(PublishBatchRow::getBatchCode).toList();
        List<PublishBatchDetailRow> details = publishRecordDao.selectDetailsByBatchCodes(batchCodes);

        Map<Long, List<PublishBatchDetailRow>> detailMap = new LinkedHashMap<>();
        for (PublishBatchDetailRow d : details) {
            detailMap.computeIfAbsent(d.getBatchCode(), k -> new ArrayList<>()).add(d);
        }

        List<PublishBatchDTO> result = batchRows.stream().map(row -> {
            PublishBatchDTO dto = new PublishBatchDTO();
            dto.setBatchCode(row.getBatchCode());
            dto.setPublishType(row.getPublishType());
            dto.setStatus(row.getStatus());
            dto.setRemark(row.getRemark());
            dto.setCreatedAt(row.getCreatedAt());
            dto.setPublishedAt(row.getPublishedAt());

            List<PublishBatchDetailRow> items = detailMap.getOrDefault(row.getBatchCode(), List.of());
            dto.setWorkflows(items.stream().map(item -> {
                PublishBatchDTO.WorkflowItem wi = new PublishBatchDTO.WorkflowItem();
                wi.setName(item.getWorkflowName());
                wi.setVersionNo(item.getVersionNo());
                wi.setStatus(item.getStatus());
                return wi;
            }).toList());
            return dto;
        }).toList();

        return PageResult.of(result, batchPage.getTotal(), pageNum, pageSize);
    }

    /**
     * 工作流发布：单个工作流，一条记录。
     */
    @Transactional
    public PublishRecordDTO submitPublish(Long workflowDefinitionCode, Long existingVersionId,
                                          Long projectCode, String remark) {
        WorkflowDefinition workflow = workflowDefinitionDao.selectByCode(workflowDefinitionCode);
        if (workflow == null) {
            throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
        }
        Project project = projectDao.selectByCode(projectCode);

        int versionNo = resolveVersionNo(workflow, existingVersionId, remark);
        Long batchCode = CodeGenerateUtils.genCode();

        PublishRecord record = new PublishRecord();
        record.setBatchCode(batchCode);
        record.setPublishType(PublishType.WORKFLOW);
        record.setProjectCode(projectCode);
        record.setWorkflowDefinitionCode(workflowDefinitionCode);
        record.setVersionNo(versionNo);
        record.setRemark(remark);
        record.setStatus(PublishStatus.PENDING_APPROVAL);
        publishRecordDao.insert(record);

        if (UserContext.isSuperAdmin()) {
            publishToTarget(batchCode, List.of(record), List.of(workflow));
        } else {
            triggerApproval(batchCode, PublishType.WORKFLOW,
                    "Workflow Publish: " + workflow.getName(),
                    "Publish workflow [" + workflow.getName() + "] v" + versionNo,
                    workflow.getWorkspaceId(), projectCode,
                    workflow.getName(), remark);
        }

        return BeanConvertUtils.convert(record, PublishRecordDTO.class);
    }

    /**
     * 项目发布：多个工作流，共享同一个 batchCode，一次审批。
     */
    @Transactional
    public List<PublishRecordDTO> submitProjectPublish(Long projectCode,
                                                       List<PublishItem> items,
                                                       String remark) {
        Project project = projectDao.selectByCode(projectCode);
        if (project == null) {
            throw new NotFoundException(WorkflowErrorCode.PROJECT_NOT_FOUND);
        }

        Long batchCode = CodeGenerateUtils.genCode();
        List<PublishRecordDTO> results = new ArrayList<>();
        List<PublishRecord> records = new ArrayList<>();
        List<WorkflowDefinition> workflows = new ArrayList<>();
        StringJoiner contentJoiner = new StringJoiner("\n");

        for (PublishItem item : items) {
            WorkflowDefinition workflow = workflowDefinitionDao.selectByCode(item.workflowDefinitionCode());
            if (workflow == null) {
                throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
            }

            int versionNo = resolveVersionNo(workflow, item.versionId(), remark);

            PublishRecord record = new PublishRecord();
            record.setBatchCode(batchCode);
            record.setPublishType(PublishType.PROJECT);
            record.setProjectCode(projectCode);
            record.setWorkflowDefinitionCode(item.workflowDefinitionCode());
            record.setVersionNo(versionNo);
            record.setRemark(remark);
            record.setStatus(PublishStatus.PENDING_APPROVAL);
            publishRecordDao.insert(record);

            records.add(record);
            workflows.add(workflow);
            results.add(BeanConvertUtils.convert(record, PublishRecordDTO.class));
            contentJoiner.add("- " + workflow.getName() + " (v" + versionNo + ")");
        }

        if (UserContext.isSuperAdmin()) {
            publishToTarget(batchCode, records, workflows);
        } else {
            triggerApproval(batchCode, PublishType.PROJECT,
                    "Project Publish: " + project.getName(),
                    "Publish project [" + project.getName() + "] with " + items.size() + " workflows:\n"
                            + contentJoiner,
                    project.getWorkspaceId(), projectCode,
                    project.getName(), remark);
        }

        return results;
    }

    /**
     * 执行发布：用户在审批通过后手动触发，将工作流发布到目标环境。
     * 仅 APPROVED 状态的批次可以执行发布。
     * 根据 publishType 区分工作流级别和项目级别。
     */
    @Transactional
    public void executePublish(Long batchCode) {
        List<PublishRecord> records = publishRecordDao.selectByBatchCode(batchCode);
        if (records.isEmpty()) {
            throw new NotFoundException(WorkflowErrorCode.PUBLISH_NOT_FOUND);
        }

        List<ApprovalRecordDTO> approvals =
                approvalService.listByResource(RESOURCE_TYPE_WORKFLOW_PUBLISH, batchCode);
        boolean allApproved = !approvals.isEmpty()
                && approvals.stream().allMatch(a -> a.getStatus() == ApprovalStatus.APPROVED);
        if (!allApproved) {
            throw new BizException(WorkflowErrorCode.PUBLISH_STATUS_INVALID,
                    "Only APPROVED batches can be published");
        }

        List<WorkflowDefinition> workflows = new ArrayList<>();
        for (PublishRecord record : records) {
            WorkflowDefinition wf = workflowDefinitionDao.selectByCode(record.getWorkflowDefinitionCode());
            if (wf == null) {
                markAllFailed(records);
                throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND,
                        record.getWorkflowDefinitionCode());
            }
            workflows.add(wf);
        }
        publishToTarget(batchCode, records, workflows);
    }

    /** 实际发布到目标调度器（不含审批校验）—— 复用给审批通过后的执行路径与 SUPER_ADMIN 跳审批路径。 */
    private void publishToTarget(Long batchCode, List<PublishRecord> records,
                                 List<WorkflowDefinition> workflows) {
        if (records.size() != workflows.size()) {
            // 调用方按下标对齐传入；不一致意味着上游构造逻辑出错，立即 fail 防错位发布
            throw new IllegalArgumentException(
                    "records and workflows must have same size: records=" + records.size()
                            + ", workflows=" + workflows.size());
        }
        Publisher publisher = publishConfigService.required();

        PublishRecord first = records.get(0);
        String userName = resolveUserName(first.getCreatedBy());

        try {
            if (first.getPublishType() == PublishType.PROJECT) {
                Project project = projectDao.selectByCode(first.getProjectCode());
                List<WorkflowBundle> wfBundles = new ArrayList<>(workflows.size());
                Set<Long> dsIds = new java.util.LinkedHashSet<>();
                Set<String> resourcePaths = new java.util.LinkedHashSet<>();
                for (WorkflowDefinition wf : workflows) {
                    WorkflowBundle wb = buildWorkflowBundle(wf);
                    wfBundles.add(wb);
                    dsIds.addAll(extractDatasourceIds(wb.getTasks()));
                    resourcePaths.addAll(extractResourcePaths(wb.getTasks()));
                }
                List<ResourceBundle> resources = loadResourceBundles(resourcePaths);
                ProjectPublishBundle projectBundle = ProjectPublishBundle.builder()
                        .projectCode(first.getProjectCode())
                        .projectName(project != null ? project.getName() : "unknown")
                        .projectDescription(project != null ? project.getDescription() : null)
                        .userName(userName)
                        .datasources(loadDatasourceBundles(dsIds))
                        .resources(resources)
                        .workflows(wfBundles)
                        .build();
                publisher.publishProject(projectBundle);
            } else {
                WorkflowDefinition wf = workflows.get(0);
                Project project = projectDao.selectByCode(wf.getProjectCode());
                WorkflowBundle wb = buildWorkflowBundle(wf);
                List<ResourceBundle> resources =
                        loadResourceBundles(extractResourcePaths(wb.getTasks()));
                WorkflowPublishBundle bundle = WorkflowPublishBundle.builder()
                        .projectCode(wf.getProjectCode())
                        .projectName(project != null ? project.getName() : null)
                        .projectDescription(project != null ? project.getDescription() : null)
                        .userName(userName)
                        .datasources(loadDatasourceBundles(extractDatasourceIds(wb.getTasks())))
                        .resources(resources)
                        .workflow(wb)
                        .build();
                publisher.publishWorkflow(bundle);
            }
        } catch (BizException e) {
            log.error("发布失败, batchCode={}, publishType={}", batchCode, first.getPublishType(), e);
            markAllFailed(records);
            throw e;
        } catch (Exception e) {
            log.error("发布失败, batchCode={}, publishType={}", batchCode, first.getPublishType(), e);
            markAllFailed(records);
            throw new BizException(WorkflowErrorCode.PUBLISH_FAILED, e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, WorkflowDefinition> wfByCode = workflows.stream()
                .collect(Collectors.toMap(WorkflowDefinition::getCode, Function.identity()));
        for (PublishRecord record : records) {
            record.setStatus(PublishStatus.PUBLISHED);
            record.setPublishedAt(now);
            publishRecordDao.updateById(record);

            // 把已发布版本 id 回写到工作流主表,用于列表上"已发布 / 草稿"判定
            WorkflowDefinition wf = wfByCode.get(record.getWorkflowDefinitionCode());
            if (wf != null && record.getVersionNo() != null) {
                Long versionId = resolveVersionId(wf.getCode(), record.getVersionNo());
                if (versionId != null) {
                    wf.setPublishedVersionId(versionId);
                    workflowDefinitionDao.updateById(wf);
                }
            }
        }
    }

    /** versionNo + workflowCode → version_record.id。找不到时返回 null,不阻断发布主流程。 */
    private Long resolveVersionId(Long workflowCode, Integer versionNo) {
        List<VersionRecord> all = versionService.list(
                io.github.zzih.rudder.common.enums.datatype.ResourceType.WORKFLOW, workflowCode);
        return all.stream()
                .filter(v -> versionNo.equals(v.getVersionNo()))
                .map(VersionRecord::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 把工作流本体(task / script / dag / schedule / globalParams)拢成中性 bundle,
     * 交给 Publisher SPI 自行翻译到具体调度器格式。项目归属与发起人由调用方在外层包装。
     */
    private WorkflowBundle buildWorkflowBundle(WorkflowDefinition workflow) {
        DagGraph dag = DagParser.parse(workflow.getDagJson());

        List<TaskDefinition> taskDefs =
                taskDefinitionDao.selectByWorkflowDefinitionCode(workflow.getCode());
        Map<Long, TaskDefinition> taskDefByCode = taskDefs.stream()
                .collect(Collectors.toMap(TaskDefinition::getCode, Function.identity()));

        Set<Long> scriptCodes = taskDefs.stream()
                .map(TaskDefinition::getScriptCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Script> scriptMap = scriptCodes.isEmpty()
                ? Map.of()
                : scriptDao.selectByCodes(scriptCodes).stream()
                        .collect(Collectors.toMap(Script::getCode, Function.identity()));

        List<TaskBundle> tasks = new ArrayList<>();
        for (DagNode node : dag.getNodes()) {
            TaskDefinition td = taskDefByCode.get(node.getTaskCode());
            if (td == null) {
                continue;
            }
            Script script = td.getScriptCode() != null ? scriptMap.get(td.getScriptCode()) : null;
            String scriptContent = script != null ? script.getContent() : null;
            scriptContent = rewriteControlFlowRefs(td.getTaskType(), scriptContent);
            tasks.add(TaskBundle.builder()
                    .taskCode(td.getCode())
                    .name(td.getName())
                    .description(td.getDescription())
                    .taskType(td.getTaskType())
                    .scriptContent(scriptContent)
                    .retryTimes(td.getRetryTimes())
                    .retryInterval(td.getRetryInterval())
                    .timeout(td.getTimeout())
                    .build());
        }

        List<EdgeBundle> edges = dag.getEdges().stream()
                .map(e -> EdgeBundle.builder()
                        .sourceTaskCode(e.getSource())
                        .targetTaskCode(e.getTarget())
                        .build())
                .toList();

        WorkflowSchedule schedule =
                workflowScheduleService.getByWorkflowDefinitionCode(workflow.getCode());
        ScheduleBundle scheduleBundle = null;
        if (schedule != null && schedule.getCronExpression() != null) {
            scheduleBundle = ScheduleBundle.builder()
                    .cronExpression(schedule.getCronExpression())
                    .timezone(schedule.getTimezone())
                    .startTime(schedule.getStartTime())
                    .endTime(schedule.getEndTime())
                    .status(schedule.getStatus() != null ? schedule.getStatus().name() : null)
                    .build();
        }

        List<Property> globalParams = workflow.getGlobalParams() != null
                && !workflow.getGlobalParams().isBlank()
                        ? JsonUtils.toList(workflow.getGlobalParams(), Property.class)
                        : List.of();

        return WorkflowBundle.builder()
                .code(workflow.getCode())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .dagJson(workflow.getDagJson())
                .tasks(tasks)
                .edges(edges)
                .schedule(scheduleBundle)
                .globalParams(globalParams != null ? globalParams : List.of())
                .build();
    }

    /** 扫 SQL 任务 scriptContent,提取所有 dataSourceId。 */
    private Set<Long> extractDatasourceIds(List<TaskBundle> tasks) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (TaskBundle task : tasks) {
            if (task.getTaskType() == null || !task.getTaskType().isSql()) {
                continue;
            }
            String content = task.getScriptContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> parsed = JsonUtils.fromJson(content, Map.class);
                Object raw = parsed != null ? parsed.get("dataSourceId") : null;
                if (raw instanceof Number n) {
                    ids.add(n.longValue());
                }
            } catch (Exception ignore) {
                // scriptContent 非 JSON 或缺字段时忽略,该 task 由 server 端按 null 处理
            }
        }
        return ids;
    }

    /** 按 id 集合查 Datasource,装配 DatasourceBundle(含 credential 解密)。 */
    private List<DatasourceBundle> loadDatasourceBundles(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return datasourceDao.selectByIds(new ArrayList<>(ids)).stream()
                .map(this::toDatasourceBundle)
                .toList();
    }

    private DatasourceBundle toDatasourceBundle(Datasource d) {
        return DatasourceBundle.builder()
                .id(d.getId())
                .name(d.getName())
                .type(d.getDatasourceType())
                .host(d.getHost())
                .port(d.getPort())
                .defaultPath(d.getDefaultPath())
                .params(d.getParams())
                .credential(decryptCredential(d.getCredential()))
                .build();
    }

    /** 解密失败不阻断发布,credential 走空,接收侧自行决定如何容忍(可拒绝该数据源 upsert)。 */
    private String decryptCredential(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.toJson(credentialService.decrypt(encrypted));
        } catch (Exception e) {
            log.warn("数据源凭证解密失败,wire 上 credential 置空: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 控制流引用 code → name 翻译。接收侧拿不到 Rudder 内部 code,只能按业务身份(name)在 DS 自身找实体。
     *
     * <ul>
     *   <li>{@code SUB_WORKFLOW}: {@code workflowDefinitionCode} → {@code workflowDefinitionName}</li>
     *   <li>{@code DEPENDENT} dependItem: {@code projectCode} → {@code projectName},
     *       {@code definitionCode} → {@code workflowDefinitionName}, {@code depTaskCode} → {@code depTaskName}</li>
     * </ul>
     *
     * 找不到对应实体时记 warn 但保留 task 继续发布(name 字段置 null,接收侧自行决定如何处理)。
     */
    @SuppressWarnings("unchecked")
    private String rewriteControlFlowRefs(TaskType taskType, String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank() || taskType == null) {
            return scriptContent;
        }
        if (taskType != TaskType.SUB_WORKFLOW && taskType != TaskType.DEPENDENT) {
            return scriptContent;
        }
        Map<String, Object> root;
        try {
            root = JsonUtils.fromJson(scriptContent, Map.class);
        } catch (Exception e) {
            log.warn("rewriteControlFlowRefs 解析 scriptContent 失败 taskType={}: {}", taskType, e.getMessage());
            return scriptContent;
        }
        if (root == null) {
            return scriptContent;
        }
        if (taskType == TaskType.SUB_WORKFLOW) {
            Object code = root.remove("workflowDefinitionCode");
            if (code instanceof Number n) {
                root.put("workflowDefinitionName",
                        resolveName(n.longValue(), "workflow", workflowDefinitionDao::selectByCode,
                                WorkflowDefinition::getName));
            }
        } else { // DEPENDENT
            Object dep = root.get("dependence");
            if (dep instanceof Map<?, ?> depMap) {
                Object list = depMap.get("dependTaskList");
                if (list instanceof List<?> taskList) {
                    for (Object t : taskList) {
                        if (!(t instanceof Map<?, ?> tm)) {
                            continue;
                        }
                        Object items = tm.get("dependItemList");
                        if (!(items instanceof List<?> itemList)) {
                            continue;
                        }
                        for (Object it : itemList) {
                            if (!(it instanceof Map<?, ?> raw)) {
                                continue;
                            }
                            Map<String, Object> item = (Map<String, Object>) raw;
                            Object pc = item.remove("projectCode");
                            if (pc instanceof Number pn) {
                                item.put("projectName", resolveName(pn.longValue(), "project",
                                        projectDao::selectByCode, Project::getName));
                            }
                            Object dc = item.remove("definitionCode");
                            if (dc instanceof Number dn) {
                                item.put("workflowDefinitionName", resolveName(dn.longValue(),
                                        "workflow", workflowDefinitionDao::selectByCode,
                                        WorkflowDefinition::getName));
                            }
                            Object tc = item.remove("depTaskCode");
                            if (tc instanceof Number tn) {
                                item.put("depTaskName", resolveName(tn.longValue(), "task",
                                        taskDefinitionDao::selectByCode, TaskDefinition::getName));
                            }
                        }
                    }
                }
            }
        }
        return JsonUtils.toJson(root);
    }

    /** 通用 code → name 查找,实体不存在时记 warn 返回 null,调用方据此决定是否原样保留旧引用。 */
    private <T> String resolveName(Long code, String kind, java.util.function.Function<Long, T> finder,
                                   java.util.function.Function<T, String> nameOf) {
        if (code == null) {
            return null;
        }
        T entity = finder.apply(code);
        if (entity == null) {
            log.warn("rewriteControlFlowRefs 找不到 {} code={}, name 置 null", kind, code);
            return null;
        }
        return nameOf.apply(entity);
    }

    /** 扫 JAR 类任务 scriptContent.jarPath, 收集所有引用到的 storage 相对路径(去重)。 */
    private Set<String> extractResourcePaths(List<TaskBundle> tasks) {
        Set<String> paths = new java.util.LinkedHashSet<>();
        for (TaskBundle task : tasks) {
            if (task.getTaskType() == null) {
                continue;
            }
            switch (task.getTaskType()) {
                case SPARK_JAR, FLINK_JAR -> {
                    String content = task.getScriptContent();
                    if (content == null || content.isBlank()) {
                        continue;
                    }
                    try {
                        Map<String, Object> parsed = JsonUtils.fromJson(content, Map.class);
                        Object jar = parsed != null ? parsed.get("jarPath") : null;
                        if (jar instanceof String s && !s.isBlank()) {
                            paths.add(s);
                        }
                    } catch (Exception ignore) {
                        // scriptContent 非 JSON 时忽略
                    }
                }
                default -> {
                }
            }
        }
        return paths;
    }

    /** 读 storage 字节并装入 ResourceBundle。大文件场景待 dedup / 流式上传落地。 */
    private List<ResourceBundle> loadResourceBundles(Set<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        FileStorage storage = fileConfigService.required();
        List<ResourceBundle> list = new ArrayList<>(paths.size());
        for (String path : paths) {
            try (var in = storage.download(path)) {
                byte[] content = in.readAllBytes();
                String name = path.substring(path.lastIndexOf('/') + 1);
                list.add(ResourceBundle.builder()
                        .path(path)
                        .name(name)
                        .size((long) content.length)
                        .sha256(CryptoUtils.sha256Hex(content))
                        .content(content)
                        .build());
            } catch (Exception e) {
                throw new BizException(WorkflowErrorCode.PUBLISH_FAILED,
                        "读取资源失败 path=" + path + ": " + e.getMessage());
            }
        }
        return list;
    }

    private void markAllFailed(List<PublishRecord> records) {
        for (PublishRecord record : records) {
            record.setStatus(PublishStatus.PUBLISH_FAILED);
            publishRecordDao.updateById(record);
        }
    }

    public record PublishItem(Long workflowDefinitionCode, Long versionId) {
    }

    /**
     * 解析版本号。如果指定了已有版本 ID，查出其 versionNo；否则自动创建新版本并返回其 versionNo。
     */
    private int resolveVersionNo(WorkflowDefinition workflow, Long existingVersionId, String remark) {
        if (existingVersionId != null) {
            VersionRecord vr = versionService.get(existingVersionId);
            if (vr == null) {
                throw new NotFoundException(WorkflowErrorCode.WF_NOT_FOUND);
            }
            return vr.getVersionNo();
        }
        return workflowVersionService.saveWorkflowVersion(workflow, workflow.getDagJson(), remark).getVersionNo();
    }

    private void triggerApproval(Long batchCode, PublishType publishType, String title,
                                 String content, Long workspaceId, Long projectCode,
                                 String name, String remark) {
        Long currentUserId = UserContext.getUserId();
        User currentUser = currentUserId != null ? userDao.selectById(currentUserId) : null;

        try {
            Map<String, String> extra = new HashMap<>();
            extra.put("batchCode", String.valueOf(batchCode));
            extra.put("publishType", publishType.name());
            // 当前用户邮箱，用于飞书审批动态解析发起人 open_id
            if (currentUser != null && currentUser.getEmail() != null) {
                extra.put(INITIATOR_EMAIL, currentUser.getEmail());
            }
            ApprovalRequest approvalRequest = ApprovalRequest.builder()
                    .title(title)
                    .content(content)
                    .extra(extra)
                    .build();
            // 阶段链由 ApprovalService 内部 StageFlow 算（依据申请人是否为项目 owner）
            approvalService.submit(approvalRequest, RESOURCE_TYPE_WORKFLOW_PUBLISH, batchCode,
                    workspaceId, projectCode, remark);
        } catch (Exception e) {
            log.warn("Failed to trigger approval notification", e);
        }

        UserRef submitter = currentUser != null
                ? UserRef.of(currentUser.getUsername(), currentUser.getEmail())
                : null;
        notificationService.notify(ApprovalSubmittedMessage.builder()
                .level(NotificationLevel.WARN)
                .resourceTitle(I18n.t("msg.approval.title.publishWorkflow") + ": " + name)
                .resourceContent(content)
                .submitter(submitter)
                .approvers(List.of())
                .remark(remark)
                .detailUrl(null)
                .build());
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return "admin";
        }
        User user = userDao.selectById(userId);
        return user != null ? user.getUsername() : "admin";
    }

}
