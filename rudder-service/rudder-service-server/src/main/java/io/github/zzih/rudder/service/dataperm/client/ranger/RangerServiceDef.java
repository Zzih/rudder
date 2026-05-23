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

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ranger service-def 反序列化。只保留 access 闭集与 resource 层级,
 * 其余 runtime 配置(validationRegEx / matcherFactory / contextEnricher 等)由
 * {@link JsonIgnoreProperties} 忽略。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RangerServiceDef implements Serializable {

    /** service-def 唯一标识(小写):"hive" / "trino" / "starrocks"。 */
    private String name;

    private List<AccessType> accessTypes;

    private List<Resource> resources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccessType implements Serializable {

        private String name;
        /** Ranger 自带分类:READ / UPDATE / CREATE / DELETE / MANAGE。"all" 等空。 */
        private String category;
        /** "all" 类 access 的隐含覆盖列表:["select","insert",...]。 */
        private List<String> impliedGrants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource implements Serializable {

        /** Ranger resource key:database / table / column / catalog / schema 等。 */
        private String name;
        /** 层级序号,自上而下递增。 */
        private Integer level;
        /** 父层级 name,顶层为空。 */
        private String parent;
    }
}
