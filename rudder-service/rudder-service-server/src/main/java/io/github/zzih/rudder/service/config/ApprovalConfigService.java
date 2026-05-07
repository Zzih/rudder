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

import io.github.zzih.rudder.approval.api.ApprovalNotifier;
import io.github.zzih.rudder.approval.api.plugin.ApprovalPluginManager;
import io.github.zzih.rudder.common.enums.error.ConfigErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.dao.ApprovalConfigDao;
import io.github.zzih.rudder.dao.entity.ApprovalConfig;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Approval active 实例的访问入口。未配置时 fallback 到 LOCAL channel。 */
@Service
@RequiredArgsConstructor
public class ApprovalConfigService {

    /** 缓存 channel + notifier 组合，避免 activeChannel 单独打 DB。 */
    public record Active(String channel, ApprovalNotifier notifier) {
    }

    private final GlobalCacheService cache;
    private final ApprovalConfigDao dao;
    private final ApprovalPluginManager pluginManager;

    public ApprovalNotifier active() {
        Active a = current();
        return a == null ? null : a.notifier();
    }

    public ApprovalNotifier required() {
        ApprovalNotifier n = active();
        if (n == null) {
            throw new BizException(ConfigErrorCode.APPROVAL_NOT_CONFIGURED);
        }
        return n;
    }

    public String activeChannel() {
        Active a = current();
        return a == null ? null : a.channel();
    }

    public void save(ApprovalConfig config) {
        if (config.getId() != null) {
            dao.updateById(config);
        } else {
            dao.insert(config);
        }
        cache.invalidate(GlobalCacheKey.APPROVAL);
    }

    /** Controller 入口:DTO → entity 取-或-新建 → 灌字段 → save。 */
    public void saveDetail(io.github.zzih.rudder.service.config.dto.ApprovalConfigDTO body) {
        ApprovalConfig c = dao.selectActive();
        if (c == null) {
            c = new ApprovalConfig();
        }
        c.setChannel(body.getChannel());
        c.setChannelParams(body.getChannelParams());
        c.setEnabled(body.getEnabled() == null || body.getEnabled());
        save(c);
    }

    public HealthStatus health() {
        Active a = current();
        return a == null ? HealthStatus.unknown() : a.notifier().healthCheck();
    }

    private Active current() {
        return cache.getOrLoad(GlobalCacheKey.APPROVAL, this::build);
    }

    private Active build() {
        ApprovalConfig c = dao.selectActive();
        if (c != null && Boolean.TRUE.equals(c.getEnabled()) && c.getChannel() != null) {
            Map<String, String> params = JsonUtils.toMap(c.getChannelParams());
            return new Active(c.getChannel(), pluginManager.create(c.getChannel(), params));
        }
        return new Active(ApprovalPluginManager.FALLBACK_CHANNEL,
                pluginManager.create(ApprovalPluginManager.FALLBACK_CHANNEL, Map.of()));
    }
}
