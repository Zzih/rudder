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

package io.github.zzih.rudder.service.config;

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiConfigDao;
import io.github.zzih.rudder.dao.dao.ApprovalConfigDao;
import io.github.zzih.rudder.dao.dao.FileConfigDao;
import io.github.zzih.rudder.dao.dao.MetadataConfigDao;
import io.github.zzih.rudder.dao.dao.NotificationConfigDao;
import io.github.zzih.rudder.dao.dao.ResultConfigDao;
import io.github.zzih.rudder.dao.dao.RuntimeConfigDao;
import io.github.zzih.rudder.dao.dao.VersionConfigDao;
import io.github.zzih.rudder.dao.entity.NotificationConfig;
import io.github.zzih.rudder.dao.enums.AiConfigType;
import io.github.zzih.rudder.service.config.dto.AiConfigDTO;
import io.github.zzih.rudder.service.config.dto.ApprovalConfigDTO;
import io.github.zzih.rudder.service.config.dto.NotificationConfigDTO;
import io.github.zzih.rudder.service.config.dto.ProviderConfigDTO;
import io.github.zzih.rudder.service.config.dto.ResultConfigDTO;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Controller 用的 config 聚合读服务:多个 *ConfigDao → DTO。
 * 增量保存逻辑在各自 *ConfigService 里;此处只做读 + DTO 转换 + workspace 范围 NotificationConfig 增删。
 */
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final FileConfigDao fileConfigDao;
    private final ResultConfigDao resultConfigDao;
    private final RuntimeConfigDao runtimeConfigDao;
    private final AiConfigDao aiConfigDao;
    private final MetadataConfigDao metadataConfigDao;
    private final ApprovalConfigDao approvalConfigDao;
    private final VersionConfigDao versionConfigDao;
    private final NotificationConfigDao notificationConfigDao;

    public ProviderConfigDTO getActiveFile() {
        return BeanConvertUtils.convert(fileConfigDao.selectActive(), ProviderConfigDTO.class);
    }

    public ResultConfigDTO getActiveResult() {
        return BeanConvertUtils.convert(resultConfigDao.selectActive(), ResultConfigDTO.class);
    }

    public ProviderConfigDTO getActiveRuntime() {
        return BeanConvertUtils.convert(runtimeConfigDao.selectActive(), ProviderConfigDTO.class);
    }

    public ProviderConfigDTO getActiveMetadata() {
        return BeanConvertUtils.convert(metadataConfigDao.selectActive(), ProviderConfigDTO.class);
    }

    public ProviderConfigDTO getActiveVersion() {
        return BeanConvertUtils.convert(versionConfigDao.selectActive(), ProviderConfigDTO.class);
    }

    public AiConfigDTO getActiveAi(AiConfigType type) {
        return BeanConvertUtils.convert(aiConfigDao.selectActive(type), AiConfigDTO.class);
    }

    public ApprovalConfigDTO getActiveApproval() {
        return BeanConvertUtils.convert(approvalConfigDao.selectActive(), ApprovalConfigDTO.class);
    }

    public NotificationConfigDTO getNotificationPlatform() {
        return BeanConvertUtils.convert(notificationConfigDao.selectPlatformConfig(), NotificationConfigDTO.class);
    }

    public NotificationConfigDTO getNotificationByWorkspaceId(Long workspaceId) {
        return BeanConvertUtils.convert(
                notificationConfigDao.selectByWorkspaceId(workspaceId), NotificationConfigDTO.class);
    }

    public NotificationConfigDTO getNotificationById(Long id) {
        return BeanConvertUtils.convert(notificationConfigDao.selectById(id), NotificationConfigDTO.class);
    }

    public List<NotificationConfigDTO> listAllNotification() {
        return BeanConvertUtils.convertList(notificationConfigDao.selectAll(), NotificationConfigDTO.class);
    }

    public void deleteNotificationByWorkspaceId(Long workspaceId) {
        notificationConfigDao.deleteByWorkspaceId(workspaceId);
    }

    /** 平台或工作空间通知配置 upsert,返回保存后的 DTO。 */
    public NotificationConfigDTO upsertNotification(Long workspaceId, NotificationConfigDTO body) {
        NotificationConfig existing = workspaceId == null
                ? notificationConfigDao.selectPlatformConfig()
                : notificationConfigDao.selectByWorkspaceId(workspaceId);
        if (existing != null) {
            // 系统管理字段(id / workspaceId / 时间戳 / 创建人)不让 DTO 改写
            BeanUtils.copyProperties(body, existing,
                    "id", "workspaceId", "createdAt", "createdBy", "updatedAt", "updatedBy");
            notificationConfigDao.updateById(existing);
            return BeanConvertUtils.convert(existing, NotificationConfigDTO.class);
        }
        NotificationConfig fresh = BeanConvertUtils.convert(body, NotificationConfig.class);
        fresh.setId(null);
        fresh.setWorkspaceId(workspaceId);
        notificationConfigDao.insert(fresh);
        return BeanConvertUtils.convert(fresh, NotificationConfigDTO.class);
    }

}
