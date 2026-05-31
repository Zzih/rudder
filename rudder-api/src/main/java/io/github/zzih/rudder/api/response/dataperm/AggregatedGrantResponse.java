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

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 「按用户」聚合视图里的一个授权来源摘要(权限包一条 / 每条直接授权一条),只带权限项计数;
 * 权限项明细经 {@code /admin/grants/items} 按来源分页懒加载,不随聚合接口下发。
 */
@Data
public class AggregatedGrantResponse {

    /** "ROLE" 或 "DIRECT". */
    private String kind;

    /** ROLE 时填权限包 id(展开时按它拉取权限项);DIRECT 时为 null。 */
    private Long bundleId;

    private String bundleName;

    private Long grantId;

    /** 该来源当前库表行(资源行)条数。 */
    private long permCount;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;
}
