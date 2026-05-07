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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.result.PageResult;
import io.github.zzih.rudder.service.audit.AuditLogService;
import io.github.zzih.rudder.service.workspace.dto.AuditLogDTO;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@RequireRole(RoleType.SUPER_ADMIN)
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResult<AuditLogDTO> page(@RequestParam(required = false) String module,
                                        @RequestParam(required = false) String action,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) String startTime,
                                        @RequestParam(required = false) String endTime,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        IPage<AuditLogDTO> page = auditLogService.pageDetail(
                module, action, username, startTime, endTime, pageNum, pageSize);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }
}
