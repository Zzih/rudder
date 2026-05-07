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

package io.github.zzih.rudder.common.utils.naming;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@code TaskInstance} / {@code WorkflowInstance} 等快照实例的命名工具。
 *
 * <p>统一格式:{@code {base}_yyyyMMdd_HHmmss_{3 位序号}},序号来自 {@code nanoTime % 1000},
 * 用于同一秒内粗粒度去重。名字不是 DB 唯一键,仅面向展示 / 日志检索,碰撞可接受。
 */
public final class InstanceNameUtils {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private InstanceNameUtils() {
    }

    /** 以当前时间构造快照名。 */
    public static String snapshotName(String base) {
        return snapshotName(base, LocalDateTime.now());
    }

    /** 以指定时间构造快照名(便于调用方复用 now 以保持多字段一致)。 */
    public static String snapshotName(String base, LocalDateTime at) {
        String timestamp = at.format(TIMESTAMP_FMT);
        String seq = String.format("%03d", System.nanoTime() % 1000);
        return base + "_" + timestamp + "_" + seq;
    }
}
