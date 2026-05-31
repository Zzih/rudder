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

package io.github.zzih.rudder.service.dataperm.adapter;

import io.github.zzih.rudder.service.dataperm.client.ranger.RangerPolicyResource;
import io.github.zzih.rudder.service.dataperm.config.PluginType;
import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;
import io.github.zzih.rudder.service.dataperm.dto.DataPermPermissionItemDTO;

import java.util.List;
import java.util.Map;

/**
 * 把 Rudder 内部权限项映射到 Ranger 端 service-def 的资源结构。
 *
 * <p>每个 {@link PluginType} 一个 {@link org.springframework.stereotype.Component @Component},通过
 * {@link RangerAdapterRegistry} 按 plugin type 查找。
 *
 * <p>**职责**:
 * <ul>
 *   <li>{@link #resourceHierarchy()} 暴露该 plugin 的层级集合(向 UI 渲染)</li>
 *   <li>{@link #supportedAccessTypes()} access 闭集(向 UI 渲染 + 入库前校验)</li>
 *   <li>{@link #toRangerResource} 把 4 元组(catalog/db/table/column)译成 Ranger resource map</li>
 *   <li>{@link #validateAccesses} 申请 / 资源包写入前校验,非闭集中字符串 fail-fast</li>
 * </ul>
 */
public interface RangerResourceAdapter {

    /** 该 adapter 服务的 {@link PluginType}。 */
    PluginType supportedPluginType();

    /** Ranger service-def 的 service type;权威信息在 {@link PluginType#serviceType()},此处仅为调用方便。 */
    default String rangerServiceType() {
        return supportedPluginType().serviceType();
    }

    /** 该 plugin 支持的 access 字符串闭集,顺序即 UI 渲染顺序。 */
    List<String> supportedAccessTypes();

    /** 该 plugin 的资源层级,顺序自上到下。 */
    List<ResourceLevel> resourceHierarchy();

    /**
     * 把权限项译为 Ranger policy 的 resources map。
     *
     * <p>规则:
     * <ul>
     *   <li>{@link #resourceHierarchy()} 中的层级必填,值缺省时按 "*" 写</li>
     *   <li>层级不在 hierarchy 中的字段忽略</li>
     *   <li>{@code isExcludes} / {@code isRecursive} 一律 false</li>
     * </ul>
     */
    Map<String, RangerPolicyResource> toRangerResource(DataPermPermissionItemDTO item);

    /**
     * 校验 access 列表全部在闭集中,非法字符串抛
     * {@link io.github.zzih.rudder.common.exception.BizException}
     * (code = {@code DataPermErrorCode.APPLICATION_INVALID})。
     */
    void validateAccesses(List<String> accesses);

    /**
     * 校验 {@link #resourceHierarchy()} 每一层均有非空值。要通配的层级须显式写
     * {@code "*"};禁止前缀闭包破缺(上级空 + 下级非空)。
     */
    void validateResources(DataPermPermissionItemDTO item);
}
