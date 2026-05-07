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

package io.github.zzih.rudder.ai.context;

import io.github.zzih.rudder.ai.dto.AiContextProfileDTO;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiContextProfileDao;
import io.github.zzih.rudder.dao.entity.AiContextProfile;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 上下文注入策略 —— scope=WORKSPACE 作为默认,SESSION 可覆盖。 */
@Service
@RequiredArgsConstructor
public class ContextProfileService {

    public static final String SCOPE_WORKSPACE = "WORKSPACE";
    public static final String SCOPE_SESSION = "SESSION";

    private final AiContextProfileDao dao;

    /** SESSION 优先;没有则回落到 WORKSPACE;都没有返回默认 profile。 */
    public AiContextProfile resolve(Long workspaceId, Long sessionId) {
        if (sessionId != null) {
            AiContextProfile s = dao.selectByScope(SCOPE_SESSION, sessionId);
            if (s != null) {
                return s;
            }
        }
        if (workspaceId != null) {
            AiContextProfile w = dao.selectByScope(SCOPE_WORKSPACE, workspaceId);
            if (w != null) {
                return w;
            }
        }
        return defaultProfile();
    }

    public AiContextProfile get(String scope, Long scopeId) {
        AiContextProfile hit = dao.selectByScope(scope, scopeId);
        return hit != null ? hit : defaultProfile();
    }

    public AiContextProfile upsert(AiContextProfile entity) {
        dao.upsert(entity);
        return entity;
    }

    public void clear(String scope, Long scopeId) {
        dao.deleteByScope(scope, scopeId);
    }

    // ==================== Detail variants — controller 调,DTO 入出 ====================

    public AiContextProfileDTO getDetail(String scope, Long scopeId) {
        return BeanConvertUtils.convert(get(scope, scopeId), AiContextProfileDTO.class);
    }

    public AiContextProfileDTO upsertDetail(AiContextProfileDTO body) {
        return BeanConvertUtils.convert(
                upsert(BeanConvertUtils.convert(body, AiContextProfile.class)),
                AiContextProfileDTO.class);
    }

    private AiContextProfile defaultProfile() {
        AiContextProfile p = new AiContextProfile();
        p.setInjectSchemaLevel("TABLES");
        p.setMaxSchemaTables(50);
        p.setInjectOpenScript(true);
        p.setInjectSelection(true);
        p.setInjectWikiRag(true);
        p.setInjectHistoryLast(10);
        return p;
    }
}
