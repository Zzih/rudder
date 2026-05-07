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

import java.util.List;

import lombok.Data;

/**
 * 创建 token 的返回。{@code plainToken} 仅本次返回明文,前端必须立即显示并提示用户保存。
 * 后续接口只能拿到 {@link McpTokenSummaryResponse#getTokenPrefix()} 前缀做识别。
 */
@Data
public class CreateMcpTokenResponse {

    private Long tokenId;
    private String plainToken;
    private McpTokenSummaryResponse token;
    private List<McpGrantInfoResponse> grants;
}
