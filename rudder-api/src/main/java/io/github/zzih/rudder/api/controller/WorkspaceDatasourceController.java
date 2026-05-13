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

import io.github.zzih.rudder.api.response.DatasourceResponse;
import io.github.zzih.rudder.api.security.annotation.RequireViewer;
import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.datasource.service.DatasourceService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 工作空间视角的 datasource 列表 —— 与 {@link DatasourceController} 拆开是为了强制工作空间隔离语义。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/datasources")
@RequiredArgsConstructor
public class WorkspaceDatasourceController {

    private final DatasourceService datasourceService;

    @GetMapping
    @RequireViewer
    public Result<List<DatasourceResponse>> list(@PathVariable Long workspaceId) {
        UserContext.assertWorkspaceAccess(workspaceId);
        return Result.ok(BeanConvertUtils.convertList(
                datasourceService.listByWorkspaceIdDetail(workspaceId), DatasourceResponse.class));
    }
}
