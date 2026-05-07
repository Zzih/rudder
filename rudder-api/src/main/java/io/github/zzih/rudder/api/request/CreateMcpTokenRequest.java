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

package io.github.zzih.rudder.api.request;

import java.util.List;

import lombok.Data;

/** 创建 MCP token 的入参。{@code expiresInDays} 上限 365,{@code capabilities} 必须在当前角色允许集合内。 */
@Data
public class CreateMcpTokenRequest {

    private String name;
    private String description;
    private Long workspaceId;
    /** 过期天数(最大 365) */
    private int expiresInDays;
    /** 申请的 capability id 列表 */
    private List<String> capabilities;
}
