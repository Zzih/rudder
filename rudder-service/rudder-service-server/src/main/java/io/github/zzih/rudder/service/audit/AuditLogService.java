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

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AuditLogDao;
import io.github.zzih.rudder.dao.entity.AuditLog;
import io.github.zzih.rudder.service.workspace.dto.AuditLogDTO;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogDao auditLogDao;

    public IPage<AuditLog> page(String module, String action, String username,
                                String startTime, String endTime,
                                int pageNum, int pageSize) {
        return auditLogDao.selectPage(module, action, username, startTime, endTime, pageNum, pageSize);
    }

    /** controller 用的 DTO 版,直接 BeanConvertUtils 转 page。 */
    public IPage<AuditLogDTO> pageDetail(String module, String action, String username,
                                         String startTime, String endTime,
                                         int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                page(module, action, username, startTime, endTime, pageNum, pageSize),
                AuditLogDTO.class);
    }
}
