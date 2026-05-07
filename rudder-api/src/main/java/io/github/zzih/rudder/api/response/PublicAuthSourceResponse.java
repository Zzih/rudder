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

import io.github.zzih.rudder.dao.enums.AuthSourceType;
import io.github.zzih.rudder.service.auth.dto.AuthSourceDTO;

import lombok.Data;

/**
 * 登录页用 —— 仅暴露非敏感字段,任何已登录 / 未登录用户都能看。
 * 渲染规则:type=PASSWORD → 渲染本地账号表单;type=LDAP → 弹账号密码表单;type=OIDC → 跳转按钮。
 */
@Data
public class PublicAuthSourceResponse {

    private Long id;
    private String name;
    private AuthSourceType type;
    private Integer priority;

    public static PublicAuthSourceResponse from(AuthSourceDTO source) {
        PublicAuthSourceResponse r = new PublicAuthSourceResponse();
        r.setId(source.getId());
        r.setName(source.getName());
        r.setType(source.getType());
        r.setPriority(source.getPriority());
        return r;
    }
}
