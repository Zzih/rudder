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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.ServiceRegistry;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ServiceRegistryMapper extends BaseMapper<ServiceRegistry> {

    ServiceRegistry queryByTypeAndHostAndPort(@Param("type") String type,
                                              @Param("host") String host,
                                              @Param("port") int port);

    List<ServiceRegistry> queryOnlineByType(@Param("type") String type);

    List<ServiceRegistry> queryAll();

    /** 仅当 status='ONLINE' 时更新心跳。返回受影响行数,0 表示该实例当前不是 ONLINE(被 cleanExpired 翻成 OFFLINE 或行不存在)。 */
    int updateHeartbeatIfOnline(@Param("type") String type,
                                @Param("host") String host,
                                @Param("port") int port,
                                @Param("heartbeat") LocalDateTime heartbeat);

    /** 强制把心跳 + status 翻回 ONLINE。给 heartbeat() 探测到 OFFLINE 后的复活路径用。 */
    int reviveAndUpdateHeartbeat(@Param("type") String type,
                                 @Param("host") String host,
                                 @Param("port") int port,
                                 @Param("heartbeat") LocalDateTime heartbeat);

    /** Atomic CAS:仅当 ONLINE 时翻 OFFLINE。返回 1=本节点翻的(可发通知),0=已被别人翻或行不在。 */
    int markOfflineIfOnline(@Param("type") String type,
                            @Param("host") String host,
                            @Param("port") int port);

    List<ServiceRegistry> queryExpiredOnline(@Param("threshold") LocalDateTime threshold);
}
