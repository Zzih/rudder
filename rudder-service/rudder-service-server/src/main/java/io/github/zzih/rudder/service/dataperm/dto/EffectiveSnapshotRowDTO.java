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

package io.github.zzih.rudder.service.dataperm.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveSnapshotRowDTO {

    private Long userId;
    private String username;
    private Long version;
    private LocalDateTime snapshotTime;
    private Long scopeCode;
    private String scopeName;
    private String catalogName;
    private String databaseName;
    private String tableName;
    private String columnName;
    private List<String> accesses;
    private List<Source> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {

        /** {@code ROLE} 或 {@code DIRECT}。 */
        private String kind;
        /** kind=ROLE 时为 role_id;kind=DIRECT 时为 direct grant id。 */
        private Long id;
        /** kind=ROLE 时填权限包名;kind=DIRECT 时 null。出口处填,JSON 反序列化阶段不填。 */
        private String name;
    }
}
