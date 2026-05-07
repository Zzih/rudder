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

package io.github.zzih.rudder.service.auth;

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.dao.dao.AuthSourceDao;
import io.github.zzih.rudder.dao.entity.AuthSource;
import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.dto.AuthSourceDTO;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证源(登录方式)的管理服务: CRUD + 启停 + 探活 + 防锁死。
 *
 * <p>config_json 的 AES 加密 / 解密由本 service 内部处理: DB 读出后立即解密为明文,
 * Authenticator 拿到的永远是明文 JSON。create/update 时调用方传明文,本 service 加密后落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSourceService {

    private final AuthSourceDao authSourceDao;
    private final AuthenticatorDispatcher authenticatorDispatcher;
    private final GlobalCacheService cache;

    @Value("${rudder.security.encrypt-key:}")
    private String encryptKey;

    /** admin 列表。configJson 已解密为明文(用于编辑表单回显);敏感字段是否脱敏由 controller 决定。 */
    public List<AuthSource> listAll() {
        return authSourceDao.selectAll().stream()
                .peek(this::decryptInPlace)
                .toList();
    }

    /**
     * 启用列表。登录页拉这个; controller 仅返回非敏感字段。
     * 走 LOCAL 缓存:本地 Caffeine + Redis 广播失效;管理员保存 source 后立即生效。
     */
    public List<AuthSource> listEnabled() {
        return cache.getOrLoad(GlobalCacheKey.AUTH_SOURCE, () -> authSourceDao.selectEnabled().stream()
                .peek(this::decryptInPlace)
                .toList());
    }

    /** 按 id 查;configJson 已解密。 */
    public AuthSource getById(Long id) {
        AuthSource src = authSourceDao.selectById(id);
        if (src == null) {
            throw new NotFoundException(WorkspaceErrorCode.AUTH_SOURCE_NOT_FOUND);
        }
        decryptInPlace(src);
        return src;
    }

    /** 拿到 id 对应的 enabled source,configJson 解密;否则抛业务异常(认证流程入口用)。 */
    public AuthSource requireEnabled(Long id) {
        AuthSource src = getById(id);
        if (!Boolean.TRUE.equals(src.getEnabled())) {
            throw new BizException(WorkspaceErrorCode.AUTH_SOURCE_DISABLED);
        }
        return src;
    }

    @Transactional
    public AuthSource create(AuthSource source) {
        validateName(source.getName(), null);
        // system 行只在 data.sql 种子里建,API 入口强制 is_system=false
        source.setIsSystem(false);
        if (source.getEnabled() == null) {
            source.setEnabled(true);
        }
        if (source.getPriority() == null) {
            source.setPriority(0);
        }
        encryptInPlace(source);
        authSourceDao.insert(source);
        cache.invalidate(GlobalCacheKey.AUTH_SOURCE);
        decryptInPlace(source);
        return source;
    }

    @Transactional
    public AuthSource update(Long id, AuthSource patch) {
        AuthSource existing = authSourceDao.selectById(id);
        if (existing == null) {
            throw new NotFoundException(WorkspaceErrorCode.AUTH_SOURCE_NOT_FOUND);
        }
        if (patch.getName() != null && !patch.getName().equals(existing.getName())) {
            validateName(patch.getName(), id);
            existing.setName(patch.getName());
        }
        if (patch.getPriority() != null) {
            existing.setPriority(patch.getPriority());
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            // 系统行: enable / configJson / type 强制保持
            existing.setEnabled(true);
        } else {
            if (patch.getEnabled() != null) {
                existing.setEnabled(patch.getEnabled());
            }
            if (patch.getType() != null) {
                existing.setType(patch.getType());
            }
            if (patch.getConfigJson() != null) {
                existing.setConfigJson(patch.getConfigJson());
                encryptInPlace(existing);
            }
        }
        authSourceDao.updateById(existing);
        cache.invalidate(GlobalCacheKey.AUTH_SOURCE);
        decryptInPlace(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        AuthSource existing = authSourceDao.selectById(id);
        if (existing == null) {
            throw new NotFoundException(WorkspaceErrorCode.AUTH_SOURCE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new BizException(WorkspaceErrorCode.AUTH_SOURCE_SYSTEM_IMMUTABLE);
        }
        // TODO Phase 2: 校验是否有用户仅能从该 source 登入(t_r_user.sso_provider=type 且 password 空)
        // 当前 Phase 直接删,失去 SSO 入口的用户由管理员之后用本地账号重置密码兜底。
        authSourceDao.deleteById(id);
        cache.invalidate(GlobalCacheKey.AUTH_SOURCE);
    }

    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        AuthSource existing = authSourceDao.selectById(id);
        if (existing == null) {
            throw new NotFoundException(WorkspaceErrorCode.AUTH_SOURCE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(existing.getIsSystem()) && !enabled) {
            throw new BizException(WorkspaceErrorCode.AUTH_SOURCE_SYSTEM_IMMUTABLE);
        }
        existing.setEnabled(enabled);
        authSourceDao.updateById(existing);
        cache.invalidate(GlobalCacheKey.AUTH_SOURCE);
    }

    /** 探活:解密 source 后调对应 Authenticator.testConnection。失败不抛异常,返回 unhealthy。 */
    public HealthStatus testConnection(Long id) {
        AuthSource src;
        try {
            src = getById(id);
        } catch (BizException e) {
            return HealthStatus.unhealthy(e.getMessage());
        }
        Authenticator authenticator = authenticatorDispatcher.require(src.getType());
        try {
            return authenticator.testConnection(src);
        } catch (Exception e) {
            log.warn("testConnection failed for source id={}", id, e);
            return HealthStatus.unhealthy(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // Detail variants — 给 controller 用的对外 API,返回 DTO 不返 entity。
    // 内部业务逻辑(Authenticator dispatch 等)继续用 entity 版,本类内部调用。
    // ========================================================================

    /** admin 列表的 DTO 版,configJson 已解密(用于编辑表单回显)。 */
    public List<AuthSourceDTO> listAllDetail() {
        return BeanConvertUtils.convertList(listAll(), AuthSourceDTO.class);
    }

    /** 启用列表的 DTO 版,登录页拉取。 */
    public List<AuthSourceDTO> listEnabledDetail() {
        return BeanConvertUtils.convertList(listEnabled(), AuthSourceDTO.class);
    }

    public AuthSourceDTO getByIdDetail(Long id) {
        return BeanConvertUtils.convert(getById(id), AuthSourceDTO.class);
    }

    public AuthSourceDTO requireEnabledDetail(Long id) {
        return BeanConvertUtils.convert(requireEnabled(id), AuthSourceDTO.class);
    }

    /** create 的 DTO 版:由 controller 把 request 的字段拆开传入,本方法负责装 entity 并 create。 */
    public AuthSourceDTO createDetail(String name, AuthSourceType type, String configJson,
                                      Boolean enabled, Integer priority) {
        AuthSource entity = new AuthSource();
        entity.setName(name);
        entity.setType(type);
        entity.setConfigJson(configJson);
        entity.setEnabled(enabled);
        entity.setPriority(priority);
        return BeanConvertUtils.convert(create(entity), AuthSourceDTO.class);
    }

    /** update 的 DTO 版:patch 字段为 null 表示不改。 */
    public AuthSourceDTO updateDetail(Long id, String name, AuthSourceType type, String configJson,
                                      Boolean enabled, Integer priority) {
        AuthSource patch = new AuthSource();
        patch.setName(name);
        patch.setType(type);
        patch.setConfigJson(configJson);
        patch.setEnabled(enabled);
        patch.setPriority(priority);
        return BeanConvertUtils.convert(update(id, patch), AuthSourceDTO.class);
    }

    private void validateName(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new BizException(WorkspaceErrorCode.AUTH_SOURCE_NAME_BLANK);
        }
        AuthSource other = authSourceDao.selectByName(name);
        if (other != null && (excludeId == null || !other.getId().equals(excludeId))) {
            throw new BizException(WorkspaceErrorCode.AUTH_SOURCE_NAME_EXISTS);
        }
    }

    /** 入口:source.configJson 是明文 JSON,加密后回写。NULL / 空串 不动。 */
    private void encryptInPlace(AuthSource source) {
        String json = source.getConfigJson();
        if (json == null || json.isBlank()) {
            return;
        }
        if (source.getType() == AuthSourceType.PASSWORD) {
            // PASSWORD 不存配置;调用方误传也不写
            source.setConfigJson(null);
            return;
        }
        source.setConfigJson(CryptoUtils.aesEncrypt(json, encryptKey));
    }

    /** 出口:source.configJson 是密文,解密为明文 JSON 后回写。NULL / 空串 不动。 */
    private void decryptInPlace(AuthSource source) {
        String cipher = source.getConfigJson();
        if (cipher == null || cipher.isBlank()) {
            return;
        }
        try {
            source.setConfigJson(CryptoUtils.aesDecrypt(cipher, encryptKey));
        } catch (Exception e) {
            log.error("Failed to decrypt auth source config id={}, name={}",
                    source.getId(), source.getName(), e);
            // 解密失败不抛 — 让上层(Authenticator.parseConfig)拿到密文反序列化失败时统一处理
        }
    }
}
