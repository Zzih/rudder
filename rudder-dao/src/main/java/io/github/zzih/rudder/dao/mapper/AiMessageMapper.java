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

import io.github.zzih.rudder.dao.entity.AiMessage;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    List<AiMessage> queryBySession(@Param("sessionId") Long sessionId);

    /**
     * 取最近 N 条消息(按 id DESC 截断后再正序返回),供 turn executor 注入历史。
     * 限 limit 是为了避免长会话全表扫 + token 窗口爆炸。
     */
    List<AiMessage> queryRecentBySession(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    int deleteBySessionId(@Param("sessionId") Long sessionId);

    int updateContent(@Param("id") Long id, @Param("content") String content);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateFinalState(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("content") String content,
                         @Param("errorMessage") String errorMessage,
                         @Param("model") String model,
                         @Param("promptTokens") Integer promptTokens,
                         @Param("completionTokens") Integer completionTokens,
                         @Param("costCents") Integer costCents,
                         @Param("latencyMs") Integer latencyMs);
}
