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

package io.github.zzih.rudder.mcp.event;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.dao.dao.McpTokenDao;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpTokenExpirySchedulerTest {

    @Mock
    private McpTokenDao tokenDao;

    @InjectMocks
    private McpTokenExpiryScheduler scheduler;

    @Test
    @DisplayName("空批次 → 不调用 markExpiredIfActive")
    void emptyBatchNoop() {
        when(tokenDao.selectExpiredActiveIds(anyInt())).thenReturn(List.of());
        scheduler.markExpired();
        verify(tokenDao, never()).markExpiredIfActive(anyLong());
    }

    @Test
    @DisplayName("有过期 token → 全部调 markExpiredIfActive")
    void marksAllExpired() {
        when(tokenDao.selectExpiredActiveIds(anyInt())).thenReturn(List.of(1L, 2L, 3L));
        when(tokenDao.markExpiredIfActive(anyLong())).thenReturn(1);

        scheduler.markExpired();

        verify(tokenDao).markExpiredIfActive(eq(1L));
        verify(tokenDao).markExpiredIfActive(eq(2L));
        verify(tokenDao).markExpiredIfActive(eq(3L));
        verify(tokenDao, times(3)).markExpiredIfActive(anyLong());
    }

    @Test
    @DisplayName("乐观锁失败（CAS=0）→ 不抛错继续处理后续 id")
    void casZeroIsTolerated() {
        when(tokenDao.selectExpiredActiveIds(anyInt())).thenReturn(List.of(1L, 2L));
        when(tokenDao.markExpiredIfActive(1L)).thenReturn(0); // 已被并发处理
        when(tokenDao.markExpiredIfActive(2L)).thenReturn(1);

        scheduler.markExpired();

        verify(tokenDao).markExpiredIfActive(eq(1L));
        verify(tokenDao).markExpiredIfActive(eq(2L));
    }
}
