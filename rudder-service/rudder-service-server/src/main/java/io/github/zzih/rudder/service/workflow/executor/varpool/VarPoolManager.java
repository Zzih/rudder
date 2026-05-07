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

package io.github.zzih.rudder.service.workflow.executor.varpool;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.enums.datatype.Direct;
import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.common.utils.placeholder.PlaceholderUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作流实例的变量池。
 * <p>
 * 内部用 {@code LinkedHashMap<prop, Property>} 索引,语义层面是 {@code List<Property>}(对齐 DolphinScheduler):
 * 持久化 / 跨节点传递的格式都是 {@code List<Property>} JSON。Map 视图仅服务两个需求:
 * (1) prop 唯一,O(1) 覆盖与查找;(2) 占位符替换喂的 {@code Map<String,String>} 通过 {@link #resolveMap()}
 * 现算。
 *
 * <p>只在 {@code WorkflowInstanceRunner} 单线程内访问,无需同步。
 */
@Slf4j
public class VarPoolManager {

    private final Map<String, Property> pool = new LinkedHashMap<>();

    /**
     * 初始化:项目参数 < 全局参数 < 运行时参数(优先级递增,后到覆盖前到)。
     * 三个入参都是 {@code List<Property>} 语义 — 项目 / 全局存的就是 {@code List<Property>} JSON;
     * 启动参数(runtimeParams)在 {@code WorkflowInstanceService.createInstance} 入口已经转成同样格式。
     */
    public void init(String projectParamsJson, List<Property> globalParams, String runtimeParamsJson) {
        putAll(JsonUtils.toList(projectParamsJson, Property.class));
        putAll(globalParams);
        putAll(JsonUtils.toList(runtimeParamsJson, Property.class));
    }

    /**
     * 占位符替换专用视图。{@code PlaceholderUtils.replacePlaceholders} 吃 {@code Map<String,String>},
     * 这里现算一份(只取非 null value)给它,别在外面缓存 — pool 持续 merge,缓存会让旧 value 漏掉。
     */
    public Map<String, String> resolveMap() {
        Map<String, String> m = new LinkedHashMap<>(pool.size());
        for (Property p : pool.values()) {
            if (p.getValue() != null) {
                m.put(p.getProp(), p.getValue());
            }
        }
        return m;
    }

    /**
     * 单遍走 pool 同时产出 nodeParams (prop→Property) + resolveMap (prop→value)。
     * Runner.processTaskNode 装填节点入参时两者都要 — 一次遍历比 snapshot()+resolveMap() 两次少一倍开销。
     *
     * @param nodeParamsOut 输出的可变 map(调用方再往里塞 declared IN default),按插入顺序保留
     * @param resolveMapOut 输出的占位符替换视图(只含非 null value)
     */
    public void fillNodeParamsAndResolveMap(Map<String, Property> nodeParamsOut,
                                            Map<String, String> resolveMapOut) {
        for (Property p : pool.values()) {
            if (p.getProp() == null) {
                continue;
            }
            nodeParamsOut.put(p.getProp(), p);
            if (p.getValue() != null) {
                resolveMapOut.put(p.getProp(), p.getValue());
            }
        }
    }

    /** 占位符替换 {@code ${var}} → 当前池中对应值。未解析的占位符保留原样(给 Shell/Python 留口子)。 */
    public String resolve(String template) {
        return PlaceholderUtils.replacePlaceholders(template, resolveMap(), true);
    }

    /**
     * 合并任务输出。按 DS 语义**只接受 {@code Direct.OUT}**,IN 一律拒绝并 warn —
     * 任务声明 IN 表示"需要从外部拿",不该反向写回 varPool。
     */
    public void merge(List<Property> outputs) {
        if (outputs == null) {
            return;
        }
        for (Property p : outputs) {
            if (p == null || p.getProp() == null) {
                continue;
            }
            if (p.getDirect() != Direct.OUT) {
                log.warn("VarPool merge: rejecting non-OUT property prop={}, direct={}", p.getProp(), p.getDirect());
                continue;
            }
            pool.put(p.getProp(), p);
        }
    }

    /** HA 接管时从持久化 JSON 还原。完全替换当前内存状态。 */
    public void restore(String varPoolJson) {
        pool.clear();
        putAll(JsonUtils.toList(varPoolJson, Property.class));
    }

    /** 不可变快照。给 SwitchTask / SubWorkflowTask 等只读消费者用。 */
    public List<Property> snapshot() {
        return List.copyOf(pool.values());
    }

    public String toJson() {
        return JsonUtils.toJson(snapshot());
    }

    /**
     * 把 {@code Map<String,String>} 包装成 {@code List<Property>}(全部 IN/VARCHAR)。
     * 启动参数 / 任务出参 Shell 解析等不带类型信息的来源用。
     */
    public static List<Property> wrapAsProperties(Map<String, String> raw, Direct direct) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .map(e -> Property.builder()
                        .prop(e.getKey())
                        .direct(direct)
                        .type(DataType.VARCHAR)
                        .value(e.getValue())
                        .build())
                .toList();
    }

    private void putAll(List<Property> props) {
        if (props == null) {
            return;
        }
        for (Property p : props) {
            if (p != null && p.getProp() != null) {
                pool.put(p.getProp(), p);
            }
        }
    }
}
