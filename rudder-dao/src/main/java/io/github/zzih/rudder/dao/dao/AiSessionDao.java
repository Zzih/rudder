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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.AiSession;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface AiSessionDao {

    AiSession selectById(Long id);

    List<AiSession> selectByWorkspaceAndUser(Long workspaceId, Long userId);

    IPage<AiSession> selectPage(Long workspaceId, Long userId, int pageNum, int pageSize);

    int insert(AiSession session);

    int updateById(AiSession session);

    int deleteById(Long id);

    /** 累加 token / cost 到 session 上(原子,用于流完成后汇总)。 */
    int incrementUsage(Long id, int promptTokens, int completionTokens, int costCents);
}
