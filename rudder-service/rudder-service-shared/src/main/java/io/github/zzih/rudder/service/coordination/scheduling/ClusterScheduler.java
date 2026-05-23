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

/**
 * 集群级单触发任务调度原语。
 *
 * <p>各节点都向 {@link #schedule} 注册同一任务,内部抢 Redis 锁,
 * 抢到的节点执行,其余节点等下轮 tick;心跳续约期间持锁,任务结束释放。
 *
 * <p>语义保证:
 * <ul>
 *   <li>任意时刻同 {@code key} 全集群最多 1 个节点在跑(锁/心跳保证)</li>
 *   <li>{@link #triggerNow} 同 key 多次调用幂等合并(打 SET 标记,内部 tick 抢锁后一次性消费)</li>
 *   <li>语义上是"上轮结束 + interval",不是墙钟对齐</li>
 * </ul>
 */
public interface ClusterScheduler {

    /**
     * 注册一个集群级单触发任务。重复注册同 key 覆盖。各节点都应在启动时注册。
     */
    void schedule(ClusterScheduledTask task);

    /**
     * 跨周期立即触发一次。打标记后由下次内部 tick 抢锁消费;
     * 同 key 在锁释放前多次调用合并为一次执行,**不排队不阻塞**。
     *
     * @param key    必须先 {@link #schedule} 注册过,否则忽略并打 warn
     * @param reason 触发原因(进 audit / 日志,便于排查为什么被触发)
     */
    void triggerNow(String key, String reason);
}
