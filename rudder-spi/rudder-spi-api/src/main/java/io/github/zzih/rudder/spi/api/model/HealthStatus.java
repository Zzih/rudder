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

package io.github.zzih.rudder.spi.api.model;

/**
 * SPI 实例的运行时健康状态。管理页可定期拉取 / 失败告警。
 */
public record HealthStatus(State state, String message) {

    public enum State {
        /** 运行正常 */
        HEALTHY,
        /** 部分能力受损但可用（例如 API 限流中） */
        DEGRADED,
        /** 不可用 */
        UNHEALTHY,
        /** provider 未实现 healthCheck，或暂时无法判定 */
        UNKNOWN
    }

    public static HealthStatus healthy() {
        return new HealthStatus(State.HEALTHY, "OK");
    }

    public static HealthStatus degraded(String message) {
        return new HealthStatus(State.DEGRADED, message);
    }

    public static HealthStatus unhealthy(String message) {
        return new HealthStatus(State.UNHEALTHY, message);
    }

    public static HealthStatus unknown() {
        return new HealthStatus(State.UNKNOWN, "");
    }
}
