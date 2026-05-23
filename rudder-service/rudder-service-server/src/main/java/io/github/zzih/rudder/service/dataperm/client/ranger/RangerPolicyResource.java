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

package io.github.zzih.rudder.service.dataperm.client.ranger;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ranger policy resource 节点。一个 policy 的 {@code resources} 字段是
 * {@code Map<String, RangerPolicyResource>}(key = service-def 层级名,
 * Hive 为 database/table/column,Trino 为 catalog/schema/table/column 等)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RangerPolicyResource {

    /** 该层级允许的值,"*" 表示该层级全部。 */
    private List<String> values;

    /** 是否取反(allow except these); 一般 false。 */
    private Boolean isExcludes;

    /** 是否递归到子层级(HDFS path 等场景常用);一般 false。 */
    private Boolean isRecursive;
}
