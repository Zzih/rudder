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

package io.github.zzih.rudder.service.coordination.scheduling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClusterScheduledTaskTest {

    private static final Runnable NOOP = () -> {
    };

    @Test
    @DisplayName("合法参数构造成功")
    void validParamsOk() {
        assertDoesNotThrow(() -> new ClusterScheduledTask(
                "key", Duration.ofMinutes(5), Duration.ofMinutes(10), NOOP));
    }

    @Test
    @DisplayName("blank key 拒绝")
    void blankKeyRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "", Duration.ofMinutes(5), Duration.ofMinutes(10), NOOP));
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                null, Duration.ofMinutes(5), Duration.ofMinutes(10), NOOP));
    }

    @Test
    @DisplayName("interval <= 0 拒绝")
    void nonPositiveIntervalRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "key", Duration.ZERO, Duration.ofMinutes(10), NOOP));
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "key", Duration.ofSeconds(-1), Duration.ofMinutes(10), NOOP));
    }

    @Test
    @DisplayName("lockTtl <= interval 拒绝 (避免锁在下一轮 tick 前过期被错抢)")
    void lockTtlMustExceedInterval() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "key", Duration.ofMinutes(5), Duration.ofMinutes(5), NOOP));
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "key", Duration.ofMinutes(5), Duration.ofMinutes(3), NOOP));
    }

    @Test
    @DisplayName("null runnable 拒绝")
    void nullRunnableRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterScheduledTask(
                "key", Duration.ofMinutes(5), Duration.ofMinutes(10), null));
    }
}
