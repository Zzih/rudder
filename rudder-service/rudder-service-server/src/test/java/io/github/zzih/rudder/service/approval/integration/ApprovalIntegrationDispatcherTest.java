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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.notification.api.model.PlainMessage;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.notification.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalIntegrationDispatcherTest {

    @Mock
    private NotificationService notificationService;

    private static ApprovalFinalizedEvent approvedEvent(String resourceType, Long resourceCode) {
        return new ApprovalFinalizedEvent(
                100L, resourceType, resourceCode,
                ApprovalFinalizedEvent.STATUS_APPROVED, 1L, 999L);
    }

    @Test
    @DisplayName("未注册 resourceType → no-op,不抛错不通知")
    void unregisteredResourceTypeNoop() {
        var dispatcher =
                new ApprovalIntegrationDispatcher(List.of(), notificationService, Runnable::run, new long[]{0, 0, 0});
        dispatcher.dispatch(approvedEvent("UNKNOWN", 1L));
        verify(notificationService, never()).notify(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("成功 → 仅调一次,不重试不通知")
    void firstAttemptSuccess() {
        AtomicInteger calls = new AtomicInteger();
        var integration = recordingIntegration("FOO", e -> calls.incrementAndGet());
        var dispatcher = new ApprovalIntegrationDispatcher(List.of(integration), notificationService, Runnable::run,
                new long[]{0, 0, 0});

        dispatcher.dispatch(approvedEvent("FOO", 1L));

        assertThat(calls.get()).isEqualTo(1);
        verify(notificationService, never()).notify(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("失败 2 次 → 第 3 次成功,不告警")
    void retryUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        var integration = recordingIntegration("FOO", e -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
        });
        var dispatcher = new ApprovalIntegrationDispatcher(List.of(integration), notificationService, Runnable::run,
                new long[]{0, 0, 0});

        dispatcher.dispatch(approvedEvent("FOO", 1L));

        assertThat(calls.get()).isEqualTo(3);
        verify(notificationService, never()).notify(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("4 次都失败 → 告警通知,只发一条 PlainMessage")
    void allRetriesFailedAlerts() {
        AtomicInteger calls = new AtomicInteger();
        var integration = recordingIntegration("FOO", e -> {
            calls.incrementAndGet();
            throw new RuntimeException("permanent");
        });
        var dispatcher = new ApprovalIntegrationDispatcher(List.of(integration), notificationService, Runnable::run,
                new long[]{0, 0, 0});

        dispatcher.dispatch(approvedEvent("FOO", 42L));

        // 共 4 次尝试 (attempt=0..3 含 MAX_RETRIES)
        assertThat(calls.get()).isEqualTo(4);
        verify(notificationService, times(1)).notify(any(PlainMessage.class));
    }

    @Test
    @DisplayName("多个 integration 注册 → 按 resourceType 路由")
    void routesByResourceType() {
        List<String> fooHits = new ArrayList<>();
        List<String> barHits = new ArrayList<>();
        var foo = recordingIntegration("FOO", e -> fooHits.add("foo-" + e.resourceCode()));
        var bar = recordingIntegration("BAR", e -> barHits.add("bar-" + e.resourceCode()));
        var dispatcher = new ApprovalIntegrationDispatcher(List.of(foo, bar), notificationService, Runnable::run,
                new long[]{0, 0, 0});

        dispatcher.dispatch(approvedEvent("FOO", 1L));
        dispatcher.dispatch(approvedEvent("BAR", 2L));
        dispatcher.dispatch(approvedEvent("FOO", 3L));

        assertThat(fooHits).containsExactly("foo-1", "foo-3");
        assertThat(barHits).containsExactly("bar-2");
    }

    private static ApprovalIntegration recordingIntegration(
                                                            String type,
                                                            java.util.function.Consumer<ApprovalFinalizedEvent> body) {
        return new ApprovalIntegration() {

            @Override
            public String resourceType() {
                return type;
            }

            @Override
            public ApprovalRequest buildRequest(Long resourceCode) {
                return ApprovalRequest.builder().build();
            }

            @Override
            public void onFinalized(ApprovalFinalizedEvent event) {
                body.accept(event);
            }
        };
    }
}
