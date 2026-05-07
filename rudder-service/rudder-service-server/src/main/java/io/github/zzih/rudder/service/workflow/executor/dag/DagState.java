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

package io.github.zzih.rudder.service.workflow.executor.dag;

import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.dag.DagParser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 图 + 每节点状态机。单线程访问（由 {@code WorkflowInstanceRunner} 主循环独占）。
 */
public final class DagState {

    public enum NodeState {

        WAITING, SUBMITTED, SUCCESS, FAILED, SKIPPED;

        public boolean isTerminal() {
            return this == SUCCESS || this == FAILED || this == SKIPPED;
        }
    }

    /** 单次就绪扫描结果。 */
    public record ReadyScan(List<Long> ready, List<Long> newlyFailedByUpstream) {
    }

    private DagGraph graph;
    private final Map<Long, NodeState> states = new LinkedHashMap<>();

    public void loadGraph(String dagSnapshot) {
        loadGraph(DagParser.parse(dagSnapshot));
    }

    /** 直接喂已构造好的 {@link DagGraph}，省掉 JSON 编解码；测试与运行时都可用。 */
    public void loadGraph(DagGraph graph) {
        this.graph = graph;
        states.clear();
        for (DagNode node : graph.getNodes()) {
            states.put(node.getTaskCode(), NodeState.WAITING);
        }
    }

    public DagGraph graph() {
        return graph;
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }

    public NodeState get(Long taskCode) {
        return states.get(taskCode);
    }

    public void set(Long taskCode, NodeState state) {
        states.put(taskCode, state);
    }

    public boolean contains(Long taskCode) {
        return states.containsKey(taskCode);
    }

    /** 所有节点都已脱离 WAITING / SUBMITTED。 */
    public boolean isAllDone() {
        return states.values().stream()
                .noneMatch(s -> s == NodeState.WAITING || s == NodeState.SUBMITTED);
    }

    /** 最终成功：全 SUCCESS / SKIPPED。 */
    public boolean isAllSuccessOrSkipped() {
        return states.values().stream()
                .allMatch(s -> s == NodeState.SUCCESS || s == NodeState.SKIPPED);
    }

    /** 是否还有已投递待回调的节点。 */
    public boolean hasSubmitted() {
        return states.containsValue(NodeState.SUBMITTED);
    }

    /** 遍历状态映射（只读视图）。 */
    public Map<Long, NodeState> snapshot() {
        return Map.copyOf(states);
    }

    /** 状态按 NodeState 分组计数，日志用。 */
    public Map<NodeState, Long> summary() {
        Map<NodeState, Long> out = new EnumMap<>(NodeState.class);
        for (NodeState s : states.values()) {
            out.merge(s, 1L, Long::sum);
        }
        return out;
    }

    /**
     * 扫描就绪节点（纯查询，不改状态）。返回 {@link ReadyScan}：
     * <ul>
     *   <li>{@code ready} — 上游全部 SUCCESS/SKIPPED 的 WAITING 节点，应当立即处理</li>
     *   <li>{@code newlyFailedByUpstream} — 上游存在 FAILED 的 WAITING 节点，调用方需要
     *       <b>逐个</b>调 {@link #set}(code, FAILED) + 写 task_instance 行。保持"状态变更与 DB 写入
     *       对每个节点原子且交错"的旧有时序，避免观察者（审计 / 时间轴监控）感知到顺序变化。</li>
     * </ul>
     */
    public ReadyScan scanReady() {
        List<Long> ready = new ArrayList<>();
        List<Long> newlyFailed = new ArrayList<>();
        for (Map.Entry<Long, NodeState> entry : states.entrySet()) {
            if (entry.getValue() != NodeState.WAITING) {
                continue;
            }
            Long taskCode = entry.getKey();
            List<Long> upstreams = graph.getUpstreamNodeCodes(taskCode);
            boolean hasFailedUpstream = upstreams.stream().anyMatch(u -> states.get(u) == NodeState.FAILED);
            if (hasFailedUpstream) {
                newlyFailed.add(taskCode);
                continue;
            }
            boolean allUpstreamDone = upstreams.stream().allMatch(u -> {
                NodeState s = states.get(u);
                return s == NodeState.SUCCESS || s == NodeState.SKIPPED;
            });
            if (allUpstreamDone) {
                ready.add(taskCode);
            }
        }
        return new ReadyScan(ready, newlyFailed);
    }

    /** InstanceStatus → NodeState（用于 HA resume 从 DB 反推节点状态）。 */
    public static NodeState mapFromTaskStatus(InstanceStatus status) {
        if (status == null) {
            return NodeState.WAITING;
        }
        return switch (status) {
            case SUCCESS -> NodeState.SUCCESS;
            case FAILED, CANCELLED -> NodeState.FAILED;
            case SKIPPED -> NodeState.SKIPPED;
            case RUNNING, PENDING -> NodeState.SUBMITTED;
        };
    }
}
