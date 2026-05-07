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
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.dao.entity.AuthSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAuthenticator implements Authenticator {

    protected final AuthService authService;

    /**
     * 解析 {@code source.configJson} 为对应 config POJO。
     * 缺失 / 解析失败抛 {@link BizException}({@link WorkspaceErrorCode#SSO_PROVIDER_NOT_ENABLED});
     * 解析失败可能源于密钥轮换,值得 WARN log 留追溯。
     */
    protected <T> T parseConfig(AuthSource source, Class<T> type) {
        String json = source == null ? null : source.getConfigJson();
        if (json == null || json.isBlank()) {
            throw new BizException(WorkspaceErrorCode.SSO_PROVIDER_NOT_ENABLED);
        }
        try {
            return JsonUtils.fromJson(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse auth source config (id={}, type={}): {}",
                    source.getId(), source.getType(), e.getMessage());
            throw new BizException(WorkspaceErrorCode.SSO_PROVIDER_NOT_ENABLED);
        }
    }
}
