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

package io.github.zzih.rudder.common.utils.process;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通用轮询工具类，消除散落在各模块中的 while + Thread.sleep 轮询模式。
 */
public class PollingUtils {

    private PollingUtils() {
    }

    /**
     * 轮询直到 evaluator 返回非 null 值（表示终态），或超过最大次数抛出异常。
     *
     * @param supplier    每轮执行的操作，返回当前状态
     * @param evaluator   判断状态：返回非 null 表示终态（成功结果），返回 null 表示继续轮询，
     *                    抛出异常表示终态（失败）
     * @param interval    轮询间隔
     * @param maxAttempts 最大轮询次数
     * @param timeoutMsg  超时异常消息
     * @param <S>         轮询获取的状态类型
     * @param <R>         返回的结果类型
     * @return evaluator 返回的非 null 结果
     */
    public static <S, R> R poll(Supplier<S> supplier,
                                Function<S, R> evaluator,
                                Duration interval,
                                int maxAttempts,
                                String timeoutMsg) {
        for (int i = 0; i < maxAttempts; i++) {
            if (i > 0 || interval.toMillis() > 0) {
                sleep(interval);
            }
            S state = supplier.get();
            R result = evaluator.apply(state);
            if (result != null) {
                return result;
            }
        }
        throw new RuntimeException(timeoutMsg);
    }

    /**
     * 无限轮询直到 checker 返回 true。
     * 调用方需自行处理中断和超时逻辑。
     *
     * @param checker  每轮检查，返回 true 表示完成
     * @param interval 轮询间隔
     */
    public static void pollForever(Supplier<Boolean> checker, Duration interval) {
        while (true) {
            sleep(interval);
            if (Boolean.TRUE.equals(checker.get())) {
                return;
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }
}
