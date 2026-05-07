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

import io.github.zzih.rudder.api.request.QuickLinkRequest;
import io.github.zzih.rudder.api.response.QuickLinkResponse;
import io.github.zzih.rudder.common.annotation.RequireRole;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.common.enums.quicklink.QuickLinkCategory;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.service.quicklink.QuickLinkService;
import io.github.zzih.rudder.service.quicklink.dto.QuickLinkDTO;

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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 首页快捷入口 / 文档链接管理。读公开（已登录），写仅 SUPER_ADMIN。 */
@RestController
@RequestMapping("/api/quick-links")
@RequiredArgsConstructor
public class QuickLinkController {

    private final QuickLinkService quickLinkService;

    @GetMapping
    public Result<List<QuickLinkResponse>> list(@RequestParam(required = false) QuickLinkCategory category,
                                                @RequestParam(required = false) Boolean onlyEnabled) {
        UserContext.UserInfo user = UserContext.get();
        boolean isAdmin = user != null && RoleType.SUPER_ADMIN.name().equals(user.getRole());
        // 非管理员永远看不到禁用项;只有管理员可以在管理 UI 看完整列表
        Boolean effectiveOnlyEnabled = isAdmin ? onlyEnabled : Boolean.TRUE;
        List<QuickLinkDTO> dtos = quickLinkService.list(category, effectiveOnlyEnabled);
        return Result.ok(BeanConvertUtils.convertList(dtos, QuickLinkResponse.class));
    }

    @PostMapping
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<QuickLinkResponse> create(@Valid @RequestBody QuickLinkRequest request) {
        QuickLinkDTO dto = quickLinkService.create(BeanConvertUtils.convert(request, QuickLinkDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, QuickLinkResponse.class));
    }

    @PutMapping("/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<QuickLinkResponse> update(@PathVariable Long id, @Valid @RequestBody QuickLinkRequest request) {
        QuickLinkDTO dto = quickLinkService.update(id, BeanConvertUtils.convert(request, QuickLinkDTO.class));
        return Result.ok(BeanConvertUtils.convert(dto, QuickLinkResponse.class));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        quickLinkService.delete(id);
        return Result.ok();
    }

    @PutMapping("/sort")
    @RequireRole(RoleType.SUPER_ADMIN)
    public Result<Void> updateSort(@RequestBody List<Long> idsInOrder) {
        quickLinkService.updateSort(idsInOrder);
        return Result.ok();
    }
}
