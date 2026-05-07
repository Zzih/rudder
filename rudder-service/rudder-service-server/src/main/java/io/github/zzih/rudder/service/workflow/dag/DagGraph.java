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

package io.github.zzih.rudder.service.workflow.dag;

import io.github.zzih.rudder.common.param.Property;

import java.util.*;
import java.util.stream.Collectors;

import lombok.Data;

@Data
public class DagGraph {

    private List<DagNode> nodes = new ArrayList<>();

    private List<DagEdge> edges = new ArrayList<>();

    private List<Property> globalParams = new ArrayList<>();

    /**
     * 返回起始节点的 taskCode 列表（没有入边的节点）。
     */
    public List<Long> getStartNodeCodes() {
        Set<Long> targets = edges.stream()
                .map(DagEdge::getTarget)
                .collect(Collectors.toSet());

        return nodes.stream()
                .map(DagNode::getTaskCode)
                .filter(code -> !targets.contains(code))
                .collect(Collectors.toList());
    }

    /**
     * 返回给定节点的下游 taskCode 列表。
     */
    public List<Long> getDownstreamNodeCodes(Long taskCode) {
        return edges.stream()
                .filter(e -> e.getSource().equals(taskCode))
                .map(DagEdge::getTarget)
                .collect(Collectors.toList());
    }

    /**
     * 返回给定节点的上游 taskCode 列表。
     */
    public List<Long> getUpstreamNodeCodes(Long taskCode) {
        return edges.stream()
                .filter(e -> e.getTarget().equals(taskCode))
                .map(DagEdge::getSource)
                .collect(Collectors.toList());
    }

    /**
     * 根据 taskCode 返回对应的 {@link DagNode}，未找到则返回 {@code null}。
     */
    public DagNode getNodeByCode(Long taskCode) {
        return nodes.stream()
                .filter(n -> n.getTaskCode().equals(taskCode))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验 DAG：检查是否无环且没有孤立引用。
     */
    public boolean validate() {
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }

        Set<Long> nodeCodes = nodes.stream()
                .map(DagNode::getTaskCode)
                .collect(Collectors.toSet());

        if (edges != null) {
            for (DagEdge edge : edges) {
                if (!nodeCodes.contains(edge.getSource()) || !nodeCodes.contains(edge.getTarget())) {
                    return false;
                }
            }
        }

        // Kahn 算法检测环
        Map<Long, Integer> inDegree = new HashMap<>();
        for (Long code : nodeCodes) {
            inDegree.put(code, 0);
        }
        if (edges != null) {
            for (DagEdge edge : edges) {
                inDegree.merge(edge.getTarget(), 1, Integer::sum);
            }
        }

        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            visited++;
            if (edges != null) {
                for (DagEdge edge : edges) {
                    if (edge.getSource().equals(current)) {
                        int newDegree = inDegree.get(edge.getTarget()) - 1;
                        inDegree.put(edge.getTarget(), newDegree);
                        if (newDegree == 0) {
                            queue.add(edge.getTarget());
                        }
                    }
                }
            }
        }

        return visited == nodeCodes.size();
    }
}
