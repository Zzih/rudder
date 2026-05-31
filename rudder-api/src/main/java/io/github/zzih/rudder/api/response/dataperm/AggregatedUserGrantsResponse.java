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

package io.github.zzih.rudder.api.response.dataperm;

import java.util.List;

import lombok.Data;

/** 管理端「按用户」聚合视图行:某用户当前的全部活跃授权摘要(权限包 + 直接授权,grants 按 kind 区分,仅计数)。 */
@Data
public class AggregatedUserGrantsResponse {

    private Long userId;

    private String username;

    private List<AggregatedGrantResponse> grants;
}
