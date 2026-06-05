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

package io.github.zzih.rudder.dao.projection;

import lombok.Data;

/**
 * 历史(失效)grant 的轻量引用,用于跨 bundle / direct 两表按失效时间统一分页;
 * 拿到一页的 (id, kind) 后再按 kind 回各自表补全明细。
 */
@Data
public class InactiveGrantRef {

    private Long id;

    /** ROLE | DIRECT。 */
    private String kind;
}
