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

package io.github.zzih.rudder.ai.session;

import io.github.zzih.rudder.ai.dto.AiMessageDTO;
import io.github.zzih.rudder.ai.dto.AiSessionDTO;
import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.AiMessageDao;
import io.github.zzih.rudder.dao.dao.AiSessionDao;
import io.github.zzih.rudder.dao.entity.AiSession;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/** AI 会话 / 消息 CRUD,把 controller 直连 dao 的逻辑收编到 service 层。 */
@Service
@RequiredArgsConstructor
public class AiSessionService {

    private final AiSessionDao sessionDao;
    private final AiMessageDao messageDao;

    public IPage<AiSessionDTO> pageDetail(Long workspaceId, Long userId, int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(
                sessionDao.selectPage(workspaceId, userId, pageNum, pageSize),
                AiSessionDTO.class);
    }

    public AiSessionDTO createDetail(Long workspaceId, String title, String mode) {
        AiSession s = new AiSession();
        s.setWorkspaceId(workspaceId == null ? 0L : workspaceId);
        s.setTitle(title == null || title.isBlank()
                ? I18n.t("msg.ai.session.defaultTitle")
                : title);
        s.setMode(mode == null ? "CHAT" : mode.toUpperCase());
        sessionDao.insert(s);
        return BeanConvertUtils.convert(s, AiSessionDTO.class);
    }

    public void update(Long id, String title, String mode) {
        AiSession s = new AiSession();
        s.setId(id);
        if (title != null) {
            s.setTitle(title);
        }
        if (mode != null) {
            s.setMode(mode.toUpperCase());
        }
        sessionDao.updateById(s);
    }

    public void delete(Long id) {
        // 级联删 message,否则会留孤儿
        messageDao.deleteBySessionId(id);
        sessionDao.deleteById(id);
    }

    public List<AiMessageDTO> listMessagesDetail(Long sessionId) {
        return BeanConvertUtils.convertList(messageDao.selectBySessionId(sessionId), AiMessageDTO.class);
    }
}
