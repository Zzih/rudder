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

package io.github.zzih.rudder.service.approval.integration;

import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.notification.api.model.NotificationEventType;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.PlainMessage;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.coordination.TransactionAfterCommit;
import io.github.zzih.rudder.service.notification.NotificationService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批终态分发器。{@code ApprovalService.publishFinalized} 在 finalize 后显式调用 {@link #dispatch}。
 *
 * <p>调用方多处在 {@code @Transactional} 内,dispatcher 通过 {@code afterCommit} 钩子把实际处理移到事务提交后,
 * 并放入小线程池异步跑 — 否则 retry + sleep 会阻塞 approve HTTP 路径并长时持有事务锁。
 */
@Slf4j
@Component
public class ApprovalIntegrationDispatcher {

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {500, 2000, 5000};

    private final Map<String, ApprovalIntegration> byType;
    private final NotificationService notificationService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "approval-integration-dispatch");
        t.setDaemon(true);
        return t;
    });

    public ApprovalIntegrationDispatcher(List<ApprovalIntegration> integrations,
                                         NotificationService notificationService) {
        this.byType = integrations.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ApprovalIntegration::resourceType,
                        Function.identity()));
        this.notificationService = notificationService;
        log.info("ApprovalIntegrationDispatcher registered: {}", byType.keySet());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /** 分发审批终态。未注册 resourceType 时静默忽略(非框架业务可能存在)。 */
    public void dispatch(ApprovalFinalizedEvent event) {
        ApprovalIntegration integration = byType.get(event.resourceType());
        if (integration == null) {
            log.debug("No ApprovalIntegration for resourceType={}", event.resourceType());
            return;
        }
        TransactionAfterCommit.run(() -> executor.execute(() -> runWithRetry(integration, event)));
    }

    private void runWithRetry(ApprovalIntegration integration, ApprovalFinalizedEvent event) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                integration.onFinalized(event);
                if (attempt > 0) {
                    log.info("ApprovalIntegration succeeded after retry: resourceType={}, code={}, attempt={}",
                            event.resourceType(), event.resourceCode(), attempt);
                }
                return;
            } catch (RuntimeException e) {
                if (attempt == MAX_RETRIES) {
                    handleFinalFailure(event, e);
                    return;
                }
                log.warn("ApprovalIntegration attempt {}/{} failed: resourceType={}, code={}, err={}",
                        attempt + 1, MAX_RETRIES, event.resourceType(), event.resourceCode(),
                        e.getMessage());
                sleepBackoff(attempt);
            }
        }
    }

    private void handleFinalFailure(ApprovalFinalizedEvent event, Throwable cause) {
        log.error("ApprovalIntegration FAILED after {} retries: resourceType={}, code={}",
                MAX_RETRIES, event.resourceType(), event.resourceCode(), cause);
        try {
            notificationService.notify(PlainMessage.builder()
                    .level(NotificationLevel.FAIL)
                    .eventType(NotificationEventType.APPROVAL)
                    .title(I18n.t("msg.approval.integrationFailure.title",
                            event.resourceType(), event.resourceCode()))
                    .content(I18n.t("msg.approval.integrationFailure.content",
                            event.resourceType(), event.resourceCode(), cause.getMessage()))
                    .build());
        } catch (Exception nfe) {
            log.warn("ApprovalIntegration failure notification also failed: {}", nfe.getMessage());
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BACKOFF_MS[attempt]);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
