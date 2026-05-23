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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.approval.api.model.ApprovalRequest;
import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.approval.event.ApprovalFinalizedEvent;
import io.github.zzih.rudder.service.workflow.ApprovalService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalSubmitFacadeTest {

    @Mock
    private ApprovalService approvalService;

    @Test
    @DisplayName("未注册 resourceType → 抛 INTEGRATION_NOT_FOUND")
    void unregisteredThrows() {
        var facade = new ApprovalSubmitFacade(List.of(), approvalService);

        assertThatThrownBy(() -> facade.submit("UNKNOWN", 1L, 10L, null, "test"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.INTEGRATION_NOT_FOUND);
    }

    @Test
    @DisplayName("happy path: buildRequest 后调 ApprovalService.submit 透传参数")
    void happyPathDelegates() {
        AtomicReference<Long> builtFor = new AtomicReference<>();
        ApprovalRequest fakeRequest = ApprovalRequest.builder()
                .title("[test]")
                .content("c")
                .build();

        ApprovalIntegration foo = new ApprovalIntegration() {

            @Override
            public String resourceType() {
                return "FOO";
            }

            @Override
            public ApprovalRequest buildRequest(Long resourceCode) {
                builtFor.set(resourceCode);
                return fakeRequest;
            }

            @Override
            public void onFinalized(ApprovalFinalizedEvent event) {
            }
        };
        when(approvalService.submit(any(ApprovalRequest.class), eq("FOO"), eq(42L), eq(7L), eq(null), eq("remark")))
                .thenReturn(123L);

        var facade = new ApprovalSubmitFacade(List.of(foo), approvalService);
        Long approvalId = facade.submit("FOO", 42L, 7L, null, "remark");

        assertThat(approvalId).isEqualTo(123L);
        assertThat(builtFor.get()).isEqualTo(42L);
        verify(approvalService).submit(fakeRequest, "FOO", 42L, 7L, null, "remark");
    }
}
