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

package io.github.zzih.rudder.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    private UserContext.UserInfo userInfo(Long userId, Long workspaceId, String role) {
        return new UserContext.UserInfo(userId, "u" + userId, workspaceId, null, role);
    }

    @Test
    @DisplayName("set/get/clear: 基础生命周期")
    void setGetClear() {
        assertThat(UserContext.isPresent()).isFalse();
        assertThat(UserContext.get()).isNull();

        UserContext.set(userInfo(1L, 42L, "DEVELOPER"));

        assertThat(UserContext.isPresent()).isTrue();
        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getUsername()).isEqualTo("u1");
        assertThat(UserContext.getWorkspaceId()).isEqualTo(42L);
        assertThat(UserContext.getRole()).isEqualTo("DEVELOPER");

        UserContext.clear();

        assertThat(UserContext.isPresent()).isFalse();
        assertThat(UserContext.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("requireWorkspaceId: 未设置时 fail-fast")
    void requireWorkspaceIdThrowsWhenAbsent() {
        assertThatThrownBy(UserContext::requireWorkspaceId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspaceId not set");

        UserContext.set(userInfo(1L, null, "DEVELOPER"));
        assertThatThrownBy(UserContext::requireWorkspaceId)
                .isInstanceOf(IllegalStateException.class);

        UserContext.set(userInfo(1L, 42L, "DEVELOPER"));
        assertThat(UserContext.requireWorkspaceId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("requireUserId / requireUsername: 未认证时抛异常")
    void requireUserIdAndUsernameFailFast() {
        assertThatThrownBy(UserContext::requireUserId).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(UserContext::requireUsername).isInstanceOf(IllegalStateException.class);

        UserContext.set(userInfo(7L, 1L, "VIEWER"));

        assertThat(UserContext.requireUserId()).isEqualTo(7L);
        assertThat(UserContext.requireUsername()).isEqualTo("u7");
    }

    @Test
    @DisplayName("runWith Runnable: 执行后恢复原上下文（含 null）")
    void runWithRunnableRestoresPrevious() {
        UserContext.runWith(userInfo(1L, 10L, "DEVELOPER"), () -> {
            assertThat(UserContext.getWorkspaceId()).isEqualTo(10L);
        });

        // 原本无上下文，runWith 后应仍为 null
        assertThat(UserContext.get()).isNull();

        UserContext.set(userInfo(1L, 10L, "DEVELOPER"));
        UserContext.runWith(userInfo(2L, 99L, "VIEWER"), () -> {
            assertThat(UserContext.getUserId()).isEqualTo(2L);
            assertThat(UserContext.getWorkspaceId()).isEqualTo(99L);
        });

        // 应恢复到 user 1 / workspace 10
        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getWorkspaceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("runWith Supplier: 返回值正常 + 恢复上下文")
    void runWithSupplierReturns() {
        UserContext.set(userInfo(1L, 10L, "DEVELOPER"));

        Long result = UserContext.runWith(userInfo(2L, 99L, "VIEWER"),
                () -> UserContext.getWorkspaceId() * 2);

        assertThat(result).isEqualTo(198L);
        assertThat(UserContext.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("runWith 内部抛异常: finally 必须恢复原上下文")
    void runWithRestoresOnException() {
        UserContext.set(userInfo(1L, 10L, "DEVELOPER"));

        assertThatThrownBy(() -> UserContext.runWith(userInfo(2L, 99L, "VIEWER"), () -> {
            throw new RuntimeException("boom");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getWorkspaceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("嵌套 runWith: 正确恢复栈")
    void nestedRunWith() {
        UserContext.set(userInfo(1L, 10L, "DEVELOPER"));

        UserContext.runWith(userInfo(2L, 20L, "VIEWER"), () -> {
            assertThat(UserContext.getUserId()).isEqualTo(2L);

            UserContext.runWith(userInfo(3L, 30L, "WORKSPACE_OWNER"), () -> {
                assertThat(UserContext.getUserId()).isEqualTo(3L);
                assertThat(UserContext.getWorkspaceId()).isEqualTo(30L);
            });

            assertThat(UserContext.getUserId()).isEqualTo(2L);
            assertThat(UserContext.getWorkspaceId()).isEqualTo(20L);
        });

        assertThat(UserContext.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("callWith Callable: 受检异常透传 + 上下文恢复")
    void callWithPropagatesCheckedException() {
        UserContext.set(userInfo(1L, 10L, "DEVELOPER"));

        assertThatThrownBy(() -> UserContext.callWith(userInfo(2L, 99L, "VIEWER"), () -> {
            throw new InterruptedException("interrupted");
        }))
                .isInstanceOf(InterruptedException.class);

        assertThat(UserContext.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("wrap Runnable: 线程池执行时回放上下文，结束自动清理")
    void wrapRunnableCapturesContextForExecutor() throws Exception {
        UserContext.set(userInfo(1L, 42L, "DEVELOPER"));

        AtomicReference<Long> seenWorkspaceId = new AtomicReference<>();
        AtomicReference<Boolean> contextLeakedAfter = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 单线程池保证我们能检查同一个工作线程在任务结束后是否清理干净
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(UserContext.wrap(() -> {
                seenWorkspaceId.set(UserContext.getWorkspaceId());
            })).get(2, TimeUnit.SECONDS);

            // 再提交一个不带 wrap 的任务，验证工作线程的 ThreadLocal 已被清理
            pool.submit(() -> {
                contextLeakedAfter.set(UserContext.isPresent());
                latch.countDown();
            });

            latch.await(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(seenWorkspaceId.get()).isEqualTo(42L);
        assertThat(contextLeakedAfter.get()).isFalse();
    }

    @Test
    @DisplayName("wrap Callable: 返回值 + 受检异常 + 不污染线程池")
    void wrapCallableCapturesContextAndPropagates() throws Exception {
        UserContext.set(userInfo(1L, 42L, "DEVELOPER"));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Long result = pool.submit(UserContext.wrap(() -> UserContext.getWorkspaceId()))
                    .get(2, TimeUnit.SECONDS);
            assertThat(result).isEqualTo(42L);

            AtomicReference<Boolean> leaked = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            pool.submit(() -> {
                leaked.set(UserContext.isPresent());
                latch.countDown();
            });
            latch.await(2, TimeUnit.SECONDS);
            assertThat(leaked.get()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("wrap: 当前无上下文时直接返回原 task，不引入开销")
    void wrapReturnsOriginalWhenContextAbsent() {
        Runnable original = () -> {
        };
        Runnable wrapped = UserContext.wrap(original);
        assertThat(wrapped).isSameAs(original);
    }

    @Test
    @DisplayName("多线程隔离: ThreadLocal 不串")
    void multiThreadIsolation() throws Exception {
        UserContext.set(userInfo(1L, 100L, "DEVELOPER"));

        AtomicReference<Long> otherThreadWorkspaceId = new AtomicReference<>();
        Thread other = new Thread(() -> {
            otherThreadWorkspaceId.set(UserContext.getWorkspaceId());
            UserContext.set(userInfo(2L, 200L, "VIEWER"));
        });
        other.start();
        other.join(2000);

        // 主线程上下文不被另一线程影响
        assertThat(UserContext.getUserId()).isEqualTo(1L);
        assertThat(UserContext.getWorkspaceId()).isEqualTo(100L);
        // 另一线程开始时看不到主线程的上下文
        assertThat(otherThreadWorkspaceId.get()).isNull();
    }
}
