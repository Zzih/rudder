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

    List<ServiceRegistry> queryAllOnline();

    List<ServiceRegistry> queryAll();

    /** Atomic CAS:仅当 ONLINE 时翻 OFFLINE 并写 heartbeat。返回 1=本节点翻的,可发通知。 */
    int markOfflineIfOnline(@Param("type") String type,
                            @Param("host") String host,
                            @Param("port") int port,
                            @Param("heartbeat") LocalDateTime heartbeat);

    /** OFFLINE 且 heartbeat 早于 threshold 的物理删除,防 pod IP 漂移导致 DB 膨胀。 */
    int deleteOfflineOlderThan(@Param("threshold") LocalDateTime threshold);
}
