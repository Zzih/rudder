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

import io.github.zzih.rudder.approval.api.model.ApprovalAction;
import io.github.zzih.rudder.approval.api.model.ApprovalCallback;
import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.approval.ApprovalStatus;
import io.github.zzih.rudder.common.enums.approval.DecisionRule;
import io.github.zzih.rudder.common.enums.approval.DecisionType;
import io.github.zzih.rudder.common.enums.error.ApprovalErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.ApprovalDecisionDao;
import io.github.zzih.rudder.dao.dao.ApprovalRecordDao;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.ApprovalDecision;
import io.github.zzih.rudder.dao.entity.ApprovalRecord;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.notification.api.model.ApprovalApprovedMessage;
import io.github.zzih.rudder.notification.api.model.ApprovalRejectedMessage;
import io.github.zzih.rudder.notification.api.model.ApprovalSubmittedMessage;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.notification.api.model.UserRef;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.config.ApprovalConfigService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.workflow.approver.ApproverResolver;
import io.github.zzih.rudder.service.workflow.approver.ApproverResolverRegistry;
import io.github.zzih.rudder.service.workflow.dto.ApprovalRecordDTO;
import io.github.zzih.rudder.service.workflow.stage.ApprovalStageFlowRegistry;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批生命周期：submit → decide（多人 ANY-1 / N-of-M）→ 终态 + 通知。
 *
 * <p>架构原则：
 * <ul>
 *   <li>候选人是规则（运行时算），不是数据 — 由 {@link ApproverResolver} 实现</li>
 *   <li>阶段链是规则（提交时锁定到 {@code stage_chain}）— 由 ApprovalStageFlow 实现</li>
 *   <li>多人决议持久化到 {@code t_r_approval_decision}（每条决议一行）</li>
 *   <li>状态机迁移走乐观锁（{@code WHERE status='PENDING'}），并发安全</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRecordDao approvalRecordDao;
    private final ApprovalDecisionDao approvalDecisionDao;
    private final UserDao userDao;
    private final ApprovalConfigService approvalConfigService;
    private final NotificationService notificationService;
    private final ApprovalStageFlowRegistry stageFlowRegistry;
    private final ApproverResolverRegistry approverResolverRegistry;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 提交审批。流程：
     * <ol>
     *   <li>StageFlow 算出阶段链 → 锁定到 record.stage_chain</li>
     *   <li>插主表（status=PENDING, current_stage=chain[0]）</li>
     *   <li>解析当前阶段候选人 user_ids → username 填充到 request.approvers</li>
     *   <li>外部 channel 调 ApprovalNotifier 拿 externalId 回写</li>
     * </ol>
     */
    @Transactional
    public Long submit(ApprovalRequest request, String resourceType, Long resourceCode,
                       Long workspaceId, Long projectCode, String submitRemark) {
        ApprovalRecord record = new ApprovalRecord();
        record.setResourceType(resourceType);
        record.setResourceCode(resourceCode);
        record.setWorkspaceId(workspaceId);
        record.setProjectCode(projectCode);
        record.setTitle(request.getTitle());
        record.setDescription(request.getContent());
        record.setSubmitRemark(submitRemark);
        record.setStatus(ApprovalStatus.PENDING);
        record.setDecisionRule(DecisionRule.ANY_1);
        record.setRequiredCount(DecisionRule.ANY_1.defaultRequiredCount());
        // BaseEntity.created_by 由现有审计切面写；此处先显式 set 防止 stage flow 提前用到
        record.setCreatedBy(UserContext.getUserId());

        List<String> stageChain = stageFlowRegistry.require(resourceType).resolveStageChain(record);
        if (stageChain == null || stageChain.isEmpty()) {
            throw new IllegalStateException("Empty stage chain for resourceType=" + resourceType);
        }
        record.setStageChain(JsonUtils.toJson(stageChain));
        record.setCurrentStage(stageChain.get(0));

        // 算各阶段候选人 user_id + 申请人 user_id 合并去重 → 一次 selectByIds 拿全部 User
        Map<String, List<Long>> stageUserIds = new LinkedHashMap<>(stageChain.size() * 2);
        Set<Long> allUserIds = new LinkedHashSet<>();
        for (String stage : stageChain) {
            List<Long> userIds = approverResolverRegistry.require(stage).resolveCandidateUserIds(record);
            stageUserIds.put(stage, userIds);
            allUserIds.addAll(userIds);
        }
        if (record.getCreatedBy() != null) {
            allUserIds.add(record.getCreatedBy());
        }
        Map<Long, User> userById = allUserIds.isEmpty()
                ? Map.of()
                : userDao.selectByIds(allUserIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        // 各阶段候选人 emails（按 stage 分发）+ 扁平 usernames（兼容字段）
        Map<String, List<String>> stageCandidates = new LinkedHashMap<>(stageChain.size() * 2);
        for (Map.Entry<String, List<Long>> entry : stageUserIds.entrySet()) {
            stageCandidates.put(entry.getKey(), pickField(entry.getValue(), userById, User::getEmail));
        }
        request.setStageCandidates(stageCandidates);
        request.setApprovers(pickField(allUserIds, userById, User::getUsername));

        // 申请人信息（外部模板用 email 反查 open_id）
        User applicant = userById.get(record.getCreatedBy());
        if (applicant != null) {
            request.setApplicantUsername(applicant.getUsername());
            request.setApplicantEmail(applicant.getEmail());
        }

        String channel = approvalConfigService.activeProvider();
        String externalId = approvalConfigService.required().submitApproval(request);
        record.setChannel(channel);
        record.setExternalApprovalId(externalId);

        approvalRecordDao.insert(record);
        log.info("Approval submitted: id={}, resourceType={}, resourceCode={}, stageChain={}, channel={}, "
                + "candidatesByStage={}",
                record.getId(), resourceType, resourceCode, stageChain, channel, stageCandidates);
        return record.getId();
    }

    public IPage<ApprovalRecordDTO> page(int pageNum, int pageSize, String status) {
        UserContext.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getUserId() : null;
        Long workspaceId = userInfo != null ? userInfo.getWorkspaceId() : null;
        int roleLevel = 0;
        if (userInfo != null && userInfo.getRole() != null) {
            roleLevel = io.github.zzih.rudder.common.enums.auth.RoleType.of(userInfo.getRole()).getLevel();
        }
        return BeanConvertUtils.convertPage(
                approvalRecordDao.selectPage(pageNum, pageSize, status, userId, roleLevel, workspaceId),
                ApprovalRecordDTO.class);
    }

    public List<ApprovalRecordDTO> listByResource(String resourceType, Long resourceCode) {
        return BeanConvertUtils.convertList(
                approvalRecordDao.selectByResourceTypeAndResourceCode(resourceType, resourceCode),
                ApprovalRecordDTO.class);
    }

    public ApprovalRecordDTO getById(Long id) {
        return BeanConvertUtils.convert(approvalRecordDao.selectById(id), ApprovalRecordDTO.class);
    }

    /**
     * 本地审批决议（UI 调）。校验当前用户是当前阶段的候选人 → 插决议子表 → 按 decision_rule 推进/终结。
     *
     * <p>并发安全：插决议表的 UNIQUE 防同人重复；主表状态迁移走乐观锁 {@code WHERE status='PENDING'}。
     */
    @Transactional
    public void decide(Long id, ApprovalAction action, Long deciderUserId, String deciderUsername, String comment) {
        ApprovalRecord record = approvalRecordDao.selectById(id);
        if (record == null) {
            throw new NotFoundException(ApprovalErrorCode.APPROVAL_NOT_FOUND, id);
        }
        if (record.getStatus() != ApprovalStatus.PENDING) {
            throw new BizException(ApprovalErrorCode.APPROVAL_ALREADY_RESOLVED);
        }

        String stage = record.getCurrentStage();
        assertCandidate(record, stage, deciderUserId);

        boolean approve = action == ApprovalAction.APPROVED;
        recordDecision(id, stage, deciderUserId, deciderUsername, approve, comment);

        if (!approve) {
            finalize(record, ApprovalStatus.REJECTED);
            return;
        }

        int approveCount = approvalDecisionDao.countApproveByStage(id, stage);
        if (approveCount < (record.getRequiredCount() == null ? 1 : record.getRequiredCount())) {
            return; // 等下一个 APPROVE
        }

        List<String> chain = record.parseStageChainList();
        int idx = chain.indexOf(stage);
        if (idx < 0 || idx == chain.size() - 1) {
            finalize(record, ApprovalStatus.APPROVED);
        } else {
            advance(record, stage, chain.get(idx + 1));
        }
    }

    /**
     * 处理外部审批回调（飞书 / Kissflow）。外部渠道的"决议者"username 来自其用户系统，
     * 我们尽力反查 user_id（按 username 查）；查不到时使用 0L 作为占位符（仅审计用）。
     */
    @Transactional
    public void resolveFromCallback(ApprovalCallback callback) {
        ApprovalRecord record = approvalRecordDao.selectByExternalApprovalId(callback.getExternalApprovalId());
        if (record == null) {
            log.warn("Approval not found for externalId: {}", callback.getExternalApprovalId());
            return;
        }
        if (record.getStatus() != ApprovalStatus.PENDING) {
            log.warn("Approval already resolved: externalId={}, status={}",
                    callback.getExternalApprovalId(), record.getStatus());
            return;
        }
        String stage = record.getCurrentStage();
        Long deciderUserId = lookupUserIdByUsername(callback.getApprover());
        boolean approve = callback.getAction() == ApprovalAction.APPROVED;

        // 外部回调可能因网络重投递；UNIQUE 冲突即幂等返回
        try {
            recordDecision(record.getId(), stage, deciderUserId,
                    callback.getApprover(), approve, callback.getComment());
        } catch (DuplicateKeyException e) {
            log.info("Duplicate external callback ignored: id={}, decider={}", record.getId(), callback.getApprover());
            return;
        }

        // 外部渠道（LARK / KISSFLOW / ...）的多阶段流转由外部模板内部完成,
        // 回调到达时整单已全部走完 → 直接 finalize, 不走 advance。
        // 仅 LOCAL 渠道需要 Rudder 控制 advance（但 LOCAL 不会走到此回调路径）。
        finalize(record, approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
    }

    /**
     * 申请人撤回（仅 PENDING 且 created_by=ownerId 才生效）。
     */
    @Transactional
    public void withdraw(Long id, Long ownerUserId, String reason) {
        ApprovalRecord record = approvalRecordDao.selectById(id);
        if (record == null) {
            throw new NotFoundException(ApprovalErrorCode.APPROVAL_NOT_FOUND, id);
        }
        int rows = approvalRecordDao.withdrawIfPending(id, ownerUserId, reason);
        if (rows == 0) {
            throw new BizException(ApprovalErrorCode.APPROVAL_ALREADY_RESOLVED);
        }
        log.info("Approval withdrawn: id={}, owner={}", id, ownerUserId);
        notifyResolved(record, ApprovalStatus.WITHDRAWN, "Withdrawn by submitter");
        publishFinalized(record, ApprovalFinalizedEvent.STATUS_WITHDRAWN, ownerUserId);
    }

    /**
     * 定时任务批量过期 PENDING 单。
     */
    @Transactional
    public int expireOverdue(int batchLimit) {
        List<Long> ids = approvalRecordDao.selectExpiredPendingIds(batchLimit);
        int expired = 0;
        for (Long id : ids) {
            int rows = approvalRecordDao.finalizeIfPending(id, ApprovalStatus.EXPIRED.name());
            if (rows > 0) {
                expired++;
                ApprovalRecord r = approvalRecordDao.selectById(id);
                if (r != null) {
                    notifyResolved(r, ApprovalStatus.EXPIRED, "Expired");
                    publishFinalized(r, ApprovalFinalizedEvent.STATUS_EXPIRED, null);
                }
            }
        }
        return expired;
    }

    /**
     * 查询某审批单的全部决议（UI 展示"谁批了"用）。
     */
    public List<ApprovalDecision> listDecisions(Long approvalId) {
        return approvalDecisionDao.selectByApprovalId(approvalId);
    }

    private void assertCandidate(ApprovalRecord record, String stage, Long userId) {
        if (userId == null) {
            throw new AuthException(ApprovalErrorCode.APPROVAL_PERMISSION_DENIED);
        }
        List<Long> candidates = approverResolverRegistry.require(stage).resolveCandidateUserIds(record);
        if (!candidates.contains(userId)) {
            throw new AuthException(ApprovalErrorCode.APPROVAL_PERMISSION_DENIED);
        }
    }

    private void recordDecision(Long approvalId, String stage, Long deciderUserId,
                                String deciderUsername, boolean approve, String remark) {
        ApprovalDecision d = new ApprovalDecision();
        d.setApprovalId(approvalId);
        d.setStage(stage);
        d.setDeciderUserId(deciderUserId == null ? 0L : deciderUserId);
        d.setDeciderUsername(deciderUsername == null ? "unknown" : deciderUsername);
        d.setDecision(approve ? DecisionType.APPROVE : DecisionType.REJECT);
        d.setDecidedAt(LocalDateTime.now());
        d.setRemark(remark);
        approvalDecisionDao.insert(d);
    }

    private void finalize(ApprovalRecord record, ApprovalStatus status) {
        int rows = approvalRecordDao.finalizeIfPending(record.getId(), status.name());
        if (rows == 0) {
            // 已被并发先决议者抢先终结。决议子表那条仍然作为审计留下。
            log.info("Approval already finalized by concurrent decider: id={}", record.getId());
            return;
        }
        record.setStatus(status);
        record.setResolvedAt(LocalDateTime.now());
        log.info("Approval finalized: id={}, status={}", record.getId(), status);
        notifyResolved(record, status, latestRemark(record.getId()));
        publishFinalized(record, status.name(), UserContext.getUserId());
    }

    private void advance(ApprovalRecord record, String oldStage, String newStage) {
        int rows = approvalRecordDao.advanceStageIfPending(record.getId(), oldStage, newStage);
        if (rows == 0) {
            log.info("Approval stage advance no-op (already moved): id={}, from={}, to={}",
                    record.getId(), oldStage, newStage);
            return;
        }
        record.setCurrentStage(newStage);
        log.info("Approval advanced: id={}, {}→{}", record.getId(), oldStage, newStage);

        UserRef submitter = loadUserRef(record.getCreatedBy());
        List<UserRef> approvers = loadStageCandidates(record, newStage);
        sendNotification(record, ApprovalSubmittedMessage.builder()
                .level(NotificationLevel.WARN)
                .resourceTitle(I18n.t("msg.approval.advanceToStage", record.getTitle(), newStage))
                .resourceContent(record.getDescription())
                .submitter(submitter)
                .approvers(approvers)
                .build());
    }

    private void notifyResolved(ApprovalRecord record, ApprovalStatus status, String reason) {
        UserRef submitter = loadUserRef(record.getCreatedBy());
        UserRef approver = loadUserRef(UserContext.getUserId());

        NotificationMessage message;
        switch (status) {
            case APPROVED -> message = ApprovalApprovedMessage.builder()
                    .level(NotificationLevel.SUCCESS)
                    .resourceTitle(record.getTitle())
                    .resourceContent(record.getDescription())
                    .submitter(submitter)
                    .approver(approver)
                    .comment(reason)
                    .build();
            // REJECTED 用 FAIL 红卡；WITHDRAWN/EXPIRED 不是真正的"驳回"，视觉用 WARN 黄卡，reason 字段携带具体动因
            case REJECTED, WITHDRAWN, EXPIRED -> message = ApprovalRejectedMessage.builder()
                    .level(status == ApprovalStatus.REJECTED ? NotificationLevel.FAIL : NotificationLevel.WARN)
                    .resourceTitle(record.getTitle())
                    .resourceContent(record.getDescription())
                    .submitter(submitter)
                    .approver(approver)
                    .reason(reason)
                    .build();
            default -> {
                log.warn("Skipping notification for unexpected resolved status: id={}, status={}",
                        record.getId(), status);
                return;
            }
        }
        sendNotification(record, message);
    }

    private String latestRemark(Long approvalId) {
        List<ApprovalDecision> decisions = approvalDecisionDao.selectByApprovalId(approvalId);
        if (decisions == null || decisions.isEmpty()) {
            return null;
        }
        return decisions.get(decisions.size() - 1).getRemark();
    }

    private UserRef loadUserRef(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userDao.selectById(userId);
        if (u == null) {
            return null;
        }
        return UserRef.of(u.getUsername(), u.getEmail());
    }

    private List<UserRef> loadStageCandidates(ApprovalRecord record, String stage) {
        try {
            List<Long> ids = approverResolverRegistry.require(stage).resolveCandidateUserIds(record);
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<User> users = userDao.selectByIds(ids);
            return users.stream()
                    .map(u -> UserRef.of(u.getUsername(), u.getEmail()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load stage candidates for notification: stage={}, recordId={}",
                    stage, record.getId(), e);
            return List.of();
        }
    }

    /**
     * 终态时发布事件 — 让 MCP / 项目发布 / 工作流发布等模块订阅审批结果做相应动作。
     * 吞异常以保证主事务不被消费者拖累。
     */
    private void publishFinalized(ApprovalRecord record, String finalStatus, Long deciderUserId) {
        try {
            eventPublisher.publishEvent(new ApprovalFinalizedEvent(
                    record.getId(),
                    record.getResourceType(),
                    record.getResourceCode(),
                    finalStatus,
                    record.getWorkspaceId(),
                    deciderUserId));
        } catch (Exception e) {
            log.warn("Failed to publish ApprovalFinalizedEvent (id={}, status={}): {}",
                    record.getId(), finalStatus, e.getMessage());
        }
    }

    /** 发审批通知，吞异常（通知失败不应阻断主流程）。 */
    private void sendNotification(ApprovalRecord record, NotificationMessage message) {
        try {
            notificationService.notify(message);
        } catch (Exception e) {
            log.warn("Failed to send approval notification (resourceType={}, resourceCode={}): {}",
                    record.getResourceType(), record.getResourceCode(), e.getMessage());
        }
    }

    /**
     * 从已批量加载的 user map 里按 id 列表抽取某字段（去 null/空），避免重复查 DB。
     */
    private static List<String> pickField(java.util.Collection<Long> userIds,
                                          Map<Long, User> userById,
                                          java.util.function.Function<User, String> field) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream()
                .map(userById::get)
                .filter(Objects::nonNull)
                .map(field)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private Long lookupUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        User user = userDao.selectByUsername(username);
        return user != null ? user.getId() : null;
    }
}
