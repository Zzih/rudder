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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.dao.enums.ServiceType;

import java.time.LocalDateTime;
import java.util.List;

public interface ServiceRegistryDao {

    ServiceRegistry selectByTypeAndHostAndPort(ServiceType type, String host, int port);

    List<ServiceRegistry> selectOnlineByType(ServiceType type);

    int insert(ServiceRegistry registry);

    int updateById(ServiceRegistry registry);

    /** UPSERT 心跳:刷 heartbeat + task_count + 把 status 回置 ONLINE(覆盖误翻场景)。 */
    int updateHeartbeat(ServiceType type, String host, int port, LocalDateTime heartbeat, int taskCount);

    /** ONLINE 且 heartbeat 早于 threshold 的待回收节点。 */
    List<ServiceRegistry> selectStaleOnline(LocalDateTime threshold);

    /** Atomic CAS:仅 ONLINE 且 heartbeat<threshold 才翻 OFFLINE,返回 1=CAS 成功。 */
    int markOfflineIfStale(Long id, LocalDateTime threshold, LocalDateTime offlineAt);

    /** Atomic CAS:ONLINE → OFFLINE 无 heartbeat 前置条件,返回 1=CAS 成功。 */
    int markOfflineIfOnline(ServiceType type, String host, int port, LocalDateTime heartbeat);

    int deleteOfflineOlderThan(LocalDateTime threshold);

    List<ServiceRegistry> selectAll();
}
