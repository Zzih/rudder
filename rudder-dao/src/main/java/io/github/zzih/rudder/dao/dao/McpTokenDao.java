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

import io.github.zzih.rudder.dao.entity.McpToken;

import java.util.List;

public interface McpTokenDao {

    void insert(McpToken token);

    McpToken selectById(Long id);

    /** list/详情路径:连带 workspace.name 一起查回。workspace 已删时 workspaceName=null。 */
    McpToken selectByIdWithWorkspaceName(Long id);

    /** 主查询路径：bcrypt 之前先按前缀拿到候选 token 行。 */
    McpToken selectByTokenPrefix(String tokenPrefix);

    List<McpToken> selectByUserId(Long userId);

    List<McpToken> selectByWorkspaceId(Long workspaceId);

    int updateById(McpToken token);

    /**
     * 撤销 token（乐观锁，仅 status=ACTIVE 才生效）。
     *
     * @return 影响行数；0 表示已被撤销 / 已过期 / 不存在
     */
    int revokeIfActive(Long id, String revokedReason);

    /** 异步更新 last_used_at/ip（不阻塞调用路径）。 */
    int touchLastUsed(Long id, String lastUsedIp);

    /** 扫描已过期但状态还是 ACTIVE 的 token id 列表（定时任务用）。 */
    List<Long> selectExpiredActiveIds(int limit);

    /** 标记过期（乐观锁，仅 ACTIVE 才生效）。 */
    int markExpiredIfActive(Long id);
}
