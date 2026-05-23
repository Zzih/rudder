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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ranger policy 完整结构,通用于发送(POST/PUT) + 接收(GET 响应)两个方向。
 *
 * <p>未列出的次要字段(version / guid / createTime / updateTime / createdBy / updatedBy / 等)
 * 由 {@link JsonIgnoreProperties#ignoreUnknown()} 容忍,反序列化时忽略。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RangerPolicy {

    /** Ranger 端 id;发送 POST(创建) 时不填。 */
    private Long id;

    /** policy name,Rudder 端命名 {@code rudder-{username}-ds{id}-{path}}。 */
    private String name;

    /** service name,数据源在 Ranger Admin 中的对应 service。 */
    private String service;

    /** service type,如 "hive" / "trino" / "spark"。GET 响应才有,POST 时可缺省。 */
    private String serviceType;

    /** 0=access policy(标准 ACL);Rudder 仅用 0。 */
    private Integer policyType;

    /** 是否启用;Rudder 一律 true。 */
    private Boolean isEnabled;

    /** 是否启用 audit;Rudder 一律 true(走 Ranger Audit)。 */
    private Boolean isAuditEnabled;

    /** key = service-def 层级名(database/table/column 等)。 */
    private Map<String, RangerPolicyResource> resources;

    /** 通常 Rudder 一个 policy 只放 1 个 item(单 user)。 */
    private List<RangerPolicyItem> policyItems;
}
