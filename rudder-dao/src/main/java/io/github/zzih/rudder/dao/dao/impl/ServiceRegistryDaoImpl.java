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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.ServiceRegistryDao;
import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.dao.enums.ServiceType;
import io.github.zzih.rudder.dao.mapper.ServiceRegistryMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ServiceRegistryDaoImpl implements ServiceRegistryDao {

    private final ServiceRegistryMapper registryMapper;

    @Override
    public ServiceRegistry selectByTypeAndHostAndPort(ServiceType type, String host, int port) {
        return registryMapper.queryByTypeAndHostAndPort(type.name(), host, port);
    }

    @Override
    public List<ServiceRegistry> selectOnlineByType(ServiceType type) {
        return registryMapper.queryOnlineByType(type.name());
    }

    @Override
    public List<ServiceRegistry> selectAllOnline() {
        return registryMapper.queryAllOnline();
    }

    @Override
    public int insert(ServiceRegistry registry) {
        return registryMapper.insert(registry);
    }

    @Override
    public int updateById(ServiceRegistry registry) {
        return registryMapper.updateById(registry);
    }

    @Override
    public int markOfflineIfOnline(ServiceType type, String host, int port, LocalDateTime heartbeat) {
        return registryMapper.markOfflineIfOnline(type.name(), host, port, heartbeat);
    }

    @Override
    public int deleteOfflineOlderThan(LocalDateTime threshold) {
        return registryMapper.deleteOfflineOlderThan(threshold);
    }

    @Override
    public List<ServiceRegistry> selectAll() {
        return registryMapper.queryAll();
    }
}
