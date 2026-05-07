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

package io.github.zzih.rudder.dao.audit;

import io.github.zzih.rudder.common.audit.AuditLogPersister;
import io.github.zzih.rudder.common.audit.AuditLogRecord;
import io.github.zzih.rudder.dao.entity.AuditLog;
import io.github.zzih.rudder.dao.mapper.AuditLogMapper;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuditLogPersisterImpl implements AuditLogPersister {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void save(AuditLogRecord record) {
        AuditLog entity = new AuditLog();
        entity.setUserId(record.userId());
        entity.setUsername(record.username());
        entity.setModule(record.module());
        entity.setAction(record.action());
        entity.setResourceType(StringUtils.trimToNull(record.resourceType()));
        entity.setResourceCode(record.resourceCode());
        entity.setDescription(StringUtils.trimToNull(record.description()));
        entity.setRequestIp(record.requestIp());
        entity.setRequestMethod(record.requestMethod());
        entity.setRequestUri(record.requestUri());
        entity.setRequestParams(record.requestParams());
        entity.setStatus(record.status());
        entity.setErrorMessage(record.errorMessage());
        entity.setDurationMs(record.durationMs());
        entity.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(entity);
    }
}
