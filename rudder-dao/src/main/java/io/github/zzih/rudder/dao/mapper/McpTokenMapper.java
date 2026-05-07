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

package io.github.zzih.rudder.dao.mapper;

import io.github.zzih.rudder.dao.entity.McpToken;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface McpTokenMapper extends BaseMapper<McpToken> {

    McpToken queryByTokenPrefix(@Param("tokenPrefix") String tokenPrefix);

    McpToken queryByIdWithWorkspaceName(@Param("id") Long id);

    List<McpToken> queryByUserId(@Param("userId") Long userId);

    List<McpToken> queryByWorkspaceId(@Param("workspaceId") Long workspaceId);

    /** 撤销：仅 status=ACTIVE 时生效。 */
    int revokeIfActive(@Param("id") Long id,
                       @Param("revokedAt") LocalDateTime revokedAt,
                       @Param("revokedReason") String revokedReason);

    /** 异步更新最近使用时间和 IP（不阻塞调用路径）。 */
    int touchLastUsed(@Param("id") Long id,
                      @Param("lastUsedAt") LocalDateTime lastUsedAt,
                      @Param("lastUsedIp") String lastUsedIp);

    /** 扫描 ACTIVE 但已超过 expires_at 的 token id 列表（定时任务批量过期）。 */
    List<Long> queryExpiredActiveIds(@Param("now") LocalDateTime now,
                                     @Param("limit") int limit);

    /** 标记过期：仅 status=ACTIVE 才生效。 */
    int markExpiredIfActive(@Param("id") Long id);
}
