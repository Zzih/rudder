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

package io.github.zzih.rudder.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;

    private Boolean isSuperAdmin;

    /**
     * 全局角色: SUPER_ADMIN 直接给 SUPER_ADMIN; 普通用户登录时尚未选 workspace, 给 VIEWER 兜底,
     * 进入工作空间后由 GET /auth/me 用 X-Workspace-Id 拉取真实工作空间内角色覆盖。
     */
    private String role;
}
