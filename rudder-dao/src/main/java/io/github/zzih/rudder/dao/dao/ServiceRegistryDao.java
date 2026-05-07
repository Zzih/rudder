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

    /** 仅当 ONLINE 时更新心跳。返回 0 表示需要走复活路径(reviveAndUpdateHeartbeat) + 发 NODE_ONLINE 通知。 */
    int updateHeartbeatIfOnline(ServiceType type, String host, int port, LocalDateTime heartbeat);

    /** 复活路径:无条件把 status 翻 ONLINE 并更新心跳。 */
    int reviveAndUpdateHeartbeat(ServiceType type, String host, int port, LocalDateTime heartbeat);

    /** Atomic CAS:仅 ONLINE → OFFLINE。返回 1 表示本节点完成翻转,可发通知。 */
    int markOfflineIfOnline(ServiceType type, String host, int port);

    List<ServiceRegistry> selectExpiredOnline(LocalDateTime threshold);

    List<ServiceRegistry> selectAll();
}
