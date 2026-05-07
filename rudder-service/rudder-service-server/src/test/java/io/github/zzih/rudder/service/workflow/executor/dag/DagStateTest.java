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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.service.workflow.dag.DagEdge;
import io.github.zzih.rudder.service.workflow.dag.DagGraph;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.service.workflow.executor.dag.DagState.NodeState;
import io.github.zzih.rudder.service.workflow.executor.dag.DagState.ReadyScan;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DagStateTest {

    private DagState state;

    @BeforeEach
    void setUp() {
        state = new DagState();
        state.loadGraph(diamondGraph());
    }

    @Test
    @DisplayName("loadGraph 将所有节点初始化为 WAITING")
    void loadGraph_initialStatesAllWaiting() {
        assertThat(state.snapshot()).containsValues(
                NodeState.WAITING, NodeState.WAITING, NodeState.WAITING, NodeState.WAITING);
        assertThat(state.isAllDone()).isFalse();
    }

    @Test
    @DisplayName("scanReady 在初始状态只把起始节点视为就绪")
    void scanReady_initiallyOnlyRoot() {
        ReadyScan scan = state.scanReady();
        assertThat(scan.ready()).containsExactly(1L);
        assertThat(scan.newlyFailedByUpstream()).isEmpty();
    }

    @Test
    @DisplayName("scanReady 是纯查询：重复调用在状态未变时结果一致")
    void scanReady_pureQuery_noSideEffects() {
        ReadyScan first = state.scanReady();
        ReadyScan second = state.scanReady();
        assertThat(first.ready()).isEqualTo(second.ready());
        assertThat(first.newlyFailedByUpstream()).isEqualTo(second.newlyFailedByUpstream());
        // states unchanged — scanReady must not mutate
        assertThat(state.get(2L)).isEqualTo(NodeState.WAITING);
        assertThat(state.get(3L)).isEqualTo(NodeState.WAITING);
    }

    @Test
    @DisplayName("scanReady 在汇合节点的全部上游完成时将其标记为就绪")
    void scanReady_convergingNodeReadyWhenAllUpstreamDone() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.SUCCESS);
        state.set(3L, NodeState.SKIPPED); // SKIPPED counts as done

        ReadyScan scan = state.scanReady();
        assertThat(scan.ready()).containsExactly(4L);
    }

    @Test
    @DisplayName("scanReady 将上游 FAILED 的节点归到 newlyFailedByUpstream 而非 ready")
    void scanReady_failedUpstreamCascades() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.FAILED);

        ReadyScan scan = state.scanReady();
        // node 3 still has SUCCESS upstream → still ready (diamond: 3 depends on 1 only)
        assertThat(scan.ready()).containsExactly(3L);
        // node 4 has FAILED upstream (2) → newlyFailed
        assertThat(scan.newlyFailedByUpstream()).containsExactly(4L);
    }

    @Test
    @DisplayName("isAllDone 在存在 WAITING 或 SUBMITTED 节点时返回 false")
    void isAllDone_whileInProgress() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.SUBMITTED);
        assertThat(state.isAllDone()).isFalse();
    }

    @Test
    @DisplayName("isAllDone 在所有节点终态时返回 true")
    void isAllDone_allTerminal() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.SUCCESS);
        state.set(3L, NodeState.SKIPPED);
        state.set(4L, NodeState.FAILED);
        assertThat(state.isAllDone()).isTrue();
        assertThat(state.isAllSuccessOrSkipped()).isFalse();
    }

    @Test
    @DisplayName("isAllSuccessOrSkipped 在无 FAILED 时返回 true")
    void isAllSuccessOrSkipped_cleanRun() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.SUCCESS);
        state.set(3L, NodeState.SKIPPED);
        state.set(4L, NodeState.SUCCESS);
        assertThat(state.isAllSuccessOrSkipped()).isTrue();
    }

    @Test
    @DisplayName("hasSubmitted 与当前 SUBMITTED 节点数保持一致")
    void hasSubmitted_reflectsState() {
        assertThat(state.hasSubmitted()).isFalse();
        state.set(1L, NodeState.SUBMITTED);
        assertThat(state.hasSubmitted()).isTrue();
        state.set(1L, NodeState.SUCCESS);
        assertThat(state.hasSubmitted()).isFalse();
    }

    @Test
    @DisplayName("summary 按 NodeState 分组计数")
    void summary_groupsByState() {
        state.set(1L, NodeState.SUCCESS);
        state.set(2L, NodeState.FAILED);
        var summary = state.summary();
        assertThat(summary.get(NodeState.SUCCESS)).isEqualTo(1L);
        assertThat(summary.get(NodeState.FAILED)).isEqualTo(1L);
        assertThat(summary.get(NodeState.WAITING)).isEqualTo(2L);
    }

    @Test
    @DisplayName("mapFromTaskStatus 将 DB 状态翻译为 NodeState")
    void mapFromTaskStatus_covers_all_cases() {
        assertThat(DagState.mapFromTaskStatus(null)).isEqualTo(NodeState.WAITING);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.SUCCESS)).isEqualTo(NodeState.SUCCESS);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.FAILED)).isEqualTo(NodeState.FAILED);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.CANCELLED)).isEqualTo(NodeState.FAILED);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.SKIPPED)).isEqualTo(NodeState.SKIPPED);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.RUNNING)).isEqualTo(NodeState.SUBMITTED);
        assertThat(DagState.mapFromTaskStatus(InstanceStatus.PENDING)).isEqualTo(NodeState.SUBMITTED);
    }

    @Test
    @DisplayName("NodeState.isTerminal 对 SUCCESS/FAILED/SKIPPED 返回 true")
    void nodeState_isTerminal() {
        assertThat(NodeState.SUCCESS.isTerminal()).isTrue();
        assertThat(NodeState.FAILED.isTerminal()).isTrue();
        assertThat(NodeState.SKIPPED.isTerminal()).isTrue();
        assertThat(NodeState.WAITING.isTerminal()).isFalse();
        assertThat(NodeState.SUBMITTED.isTerminal()).isFalse();
    }

    /**
     * 构造一个菱形 DAG：1 → 2 → 4，1 → 3 → 4。
     * 用于验证汇合 / 分叉 / 级联失败等场景。
     */
    private static DagGraph diamondGraph() {
        DagGraph graph = new DagGraph();
        graph.setNodes(List.of(node(1L), node(2L), node(3L), node(4L)));
        graph.setEdges(List.of(edge(1L, 2L), edge(1L, 3L), edge(2L, 4L), edge(3L, 4L)));
        return graph;
    }

    private static DagNode node(Long code) {
        DagNode n = new DagNode();
        n.setTaskCode(code);
        n.setLabel("node-" + code);
        return n;
    }

    private static DagEdge edge(Long from, Long to) {
        DagEdge e = new DagEdge();
        e.setSource(from);
        e.setTarget(to);
        return e;
    }
}
