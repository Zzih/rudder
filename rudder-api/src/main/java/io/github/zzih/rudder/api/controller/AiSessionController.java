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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.ai.session.AiSessionService;
import io.github.zzih.rudder.api.request.AiSessionCreateRequest;
import io.github.zzih.rudder.api.request.AiSessionUpdateRequest;
import io.github.zzih.rudder.api.response.AiMessageResponse;
import io.github.zzih.rudder.api.response.AiSessionResponse;
import io.github.zzih.rudder.api.security.annotation.RequireDeveloper;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/** AI 会话 CRUD。 */
@RestController
@RequestMapping("/api/ai/sessions")
@RequiredArgsConstructor
@RequireDeveloper
public class AiSessionController {

    private final AiSessionService aiSessionService;

    @GetMapping
    public Result<IPage<AiSessionResponse>> list(
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        Long workspaceId = UserContext.requireWorkspaceId();
        return Result.ok(BeanConvertUtils.convertPage(
                aiSessionService.pageDetail(workspaceId, UserContext.getUserId(), pageNum, pageSize),
                AiSessionResponse.class));
    }

    @PostMapping
    public Result<AiSessionResponse> create(@RequestBody AiSessionCreateRequest request) {
        Long workspaceId = UserContext.requireWorkspaceId();
        return Result.ok(BeanConvertUtils.convert(
                aiSessionService.createDetail(workspaceId, request.getTitle(), request.getMode()),
                AiSessionResponse.class));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AiSessionUpdateRequest request) {
        aiSessionService.update(id, request.getTitle(), request.getMode());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiSessionService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<List<AiMessageResponse>> messages(@PathVariable Long id) {
        return Result.ok(BeanConvertUtils.convertList(
                aiSessionService.listMessagesDetail(id), AiMessageResponse.class));
    }
}
