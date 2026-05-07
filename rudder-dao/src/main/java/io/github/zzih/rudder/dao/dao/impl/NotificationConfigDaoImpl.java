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

import io.github.zzih.rudder.dao.dao.NotificationConfigDao;
import io.github.zzih.rudder.dao.entity.NotificationConfig;
import io.github.zzih.rudder.dao.mapper.NotificationConfigMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationConfigDaoImpl implements NotificationConfigDao {

    private final NotificationConfigMapper mapper;

    @Override
    public List<NotificationConfig> selectAll() {
        return mapper.selectAllOrdered();
    }

    @Override
    public NotificationConfig selectById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public NotificationConfig selectByWorkspaceId(Long workspaceId) {
        return mapper.queryByWorkspaceId(workspaceId);
    }

    @Override
    public NotificationConfig selectPlatformConfig() {
        return mapper.queryPlatformConfig();
    }

    @Override
    public int insert(NotificationConfig config) {
        return mapper.insert(config);
    }

    @Override
    public int updateById(NotificationConfig config) {
        return mapper.updateById(config);
    }

    @Override
    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    @Override
    public int deleteByWorkspaceId(Long workspaceId) {
        return mapper.deleteByWorkspaceId(workspaceId);
    }
}
