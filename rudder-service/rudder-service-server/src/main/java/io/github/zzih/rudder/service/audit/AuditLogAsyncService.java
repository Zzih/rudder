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

package io.github.zzih.rudder.service.audit;

import io.github.zzih.rudder.common.audit.AuditLogPersister;
import io.github.zzih.rudder.common.audit.AuditLogRecord;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 异步把 {@link AuditLogRecord} 落库,失败只 warn,不阻断业务请求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogAsyncService {

    private final AuditLogPersister auditLogPersister;

    @Async
    public void saveAuditLog(AuditLogRecord record) {
        try {
            auditLogPersister.save(record);
        } catch (Exception e) {
            log.warn("Failed to persist audit log: {}", e.getMessage());
        }
    }
}
