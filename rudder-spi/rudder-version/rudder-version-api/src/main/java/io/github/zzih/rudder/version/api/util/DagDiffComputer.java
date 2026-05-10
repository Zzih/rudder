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

package io.github.zzih.rudder.version.api.util;

import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.version.api.model.*;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes structural DAG diff between two workflow version snapshots.
 * <p>
 * Snapshot JSON format:
 * <pre>
 * {
 *   "dagJson": "{\"nodes\":[...], \"edges\":[...]}",  // string or object
 *   "taskDefinitions": [
 *     { "code": 123, "name": "task_name", "taskType": "HIVE", "scriptContent": "..." }
 *   ]
 * }
 * </pre>
 */
@Slf4j
public final class DagDiffComputer {

    private DagDiffComputer() {
    }

    /**
     * Build a complete DiffResult, choosing DAG or TEXT mode based on resourceType.
     */
    public static DiffResult buildDiff(Long versionIdA, Long versionIdB,
                                       String contentA, String contentB,
                                       ResourceType resourceType) {
        DiffResult result = new DiffResult();
        result.setVersionIdA(versionIdA);
        result.setVersionIdB(versionIdB);

        String safeA = contentA != null ? contentA : "";
        String safeB = contentB != null ? contentB : "";

        if (resourceType == ResourceType.WORKFLOW) {
            result.setDiffType(DiffType.DAG);
            result.setDagDiff(compute(safeA, safeB));
        } else {
            result.setDiffType(DiffType.TEXT);
        }
        result.setLines(computeLineDiff(safeA, safeB));
        return result;
    }

    /**
     * Compute structural DAG diff between two workflow snapshot JSONs.
     */
    public static DagDiffResult compute(String contentA, String contentB) {
        Snapshot oldSnap = parseSnapshot(contentA);
        Snapshot newSnap = parseSnapshot(contentB);

        Map<String, SnapNode> oldNodeMap = toMap(oldSnap.nodes);
        Map<String, SnapNode> newNodeMap = toMap(newSnap.nodes);
        Map<String, SnapTask> oldTaskMap = toTaskMap(oldSnap.tasks);
        Map<String, SnapTask> newTaskMap = toTaskMap(newSnap.tasks);
        Map<String, String> oldLabelMap = buildLabelMap(oldSnap.nodes);
        Map<String, String> newLabelMap = buildLabelMap(newSnap.nodes);

        // ---- Node diffs ----
        List<NodeDiff> nodes = new ArrayList<>();

        for (var entry : newNodeMap.entrySet()) {
            String code = entry.getKey();
            SnapNode newNode = entry.getValue();
            if (!oldNodeMap.containsKey(code)) {
                NodeDiff nd = new NodeDiff();
                nd.setNodeCode(code);
                nd.setLabel(newNode.label);
                nd.setAction(DiffAction.ADDED);
                nd.setTaskType(taskType(newTaskMap, code));
                nodes.add(nd);
            } else {
                SnapNode oldNode = oldNodeMap.get(code);
                boolean renamed = !Objects.equals(oldNode.label, newNode.label);

                SnapTask oldTask = oldTaskMap.get(code);
                SnapTask newTask = newTaskMap.get(code);
                boolean contentChanged = hasContentChanged(oldTask, newTask);
                List<DiffLine> contentDiff = null;
                if (contentChanged && oldTask != null && newTask != null) {
                    contentDiff = computeLineDiff(
                            oldTask.scriptContent != null ? oldTask.scriptContent : "",
                            newTask.scriptContent != null ? newTask.scriptContent : "");
                }

                if (renamed || contentChanged) {
                    NodeDiff nd = new NodeDiff();
                    nd.setNodeCode(code);
                    nd.setLabel(newNode.label);
                    nd.setAction(DiffAction.MODIFIED);
                    nd.setOldLabel(renamed ? oldNode.label : null);
                    nd.setTaskType(taskType(newTaskMap, code));
                    nd.setContentDiff(contentDiff);
                    nodes.add(nd);
                }
            }
        }

        for (var entry : oldNodeMap.entrySet()) {
            String code = entry.getKey();
            if (!newNodeMap.containsKey(code)) {
                NodeDiff nd = new NodeDiff();
                nd.setNodeCode(code);
                nd.setLabel(entry.getValue().label);
                nd.setAction(DiffAction.REMOVED);
                nd.setTaskType(taskType(oldTaskMap, code));
                nodes.add(nd);
            }
        }

        // ---- Edge diffs ----
        List<EdgeDiff> edges = new ArrayList<>();
        Set<String> oldEdgeKeys = new HashSet<>();
        for (SnapEdge e : oldSnap.edges) {
            oldEdgeKeys.add(e.source + "->" + e.target);
        }
        Set<String> newEdgeKeys = new HashSet<>();
        for (SnapEdge e : newSnap.edges) {
            newEdgeKeys.add(e.source + "->" + e.target);
        }

        for (SnapEdge e : newSnap.edges) {
            if (!oldEdgeKeys.contains(e.source + "->" + e.target)) {
                EdgeDiff ed = new EdgeDiff();
                ed.setSource(newLabelMap.getOrDefault(e.source, e.source));
                ed.setTarget(newLabelMap.getOrDefault(e.target, e.target));
                ed.setAction(DiffAction.ADDED);
                edges.add(ed);
            }
        }
        for (SnapEdge e : oldSnap.edges) {
            if (!newEdgeKeys.contains(e.source + "->" + e.target)) {
                EdgeDiff ed = new EdgeDiff();
                ed.setSource(oldLabelMap.getOrDefault(e.source, e.source));
                ed.setTarget(oldLabelMap.getOrDefault(e.target, e.target));
                ed.setAction(DiffAction.REMOVED);
                edges.add(ed);
            }
        }

        DagDiffResult result = new DagDiffResult();
        result.setNodes(nodes);
        result.setEdges(edges);
        result.setOldNodeCount(oldSnap.nodes.size());
        result.setNewNodeCount(newSnap.nodes.size());
        return result;
    }

    /**
     * Line-by-line text diff (shared by both DB and Git stores).
     */
    public static List<DiffLine> computeLineDiff(String a, String b) {
        List<DiffLine> lines = new ArrayList<>();
        String[] linesA = a.split("\n", -1);
        String[] linesB = b.split("\n", -1);

        int maxLen = Math.max(linesA.length, linesB.length);
        for (int i = 0; i < maxLen; i++) {
            String lineA = i < linesA.length ? linesA[i] : null;
            String lineB = i < linesB.length ? linesB[i] : null;

            if (lineA == null) {
                DiffLine dl = new DiffLine();
                dl.setLineNumber(i + 1);
                dl.setAction(DiffAction.ADDED);
                dl.setContent(lineB);
                lines.add(dl);
            } else if (lineB == null) {
                DiffLine dl = new DiffLine();
                dl.setLineNumber(i + 1);
                dl.setAction(DiffAction.REMOVED);
                dl.setContent(lineA);
                lines.add(dl);
            } else if (!lineA.equals(lineB)) {
                DiffLine dl = new DiffLine();
                dl.setLineNumber(i + 1);
                dl.setAction(DiffAction.MODIFIED);
                dl.setContent(lineB);
                lines.add(dl);
            }
        }
        return lines;
    }

    // ==================== Internal Helpers ====================

    private static Snapshot parseSnapshot(String raw) {
        Snapshot snap = new Snapshot();
        if (raw == null || raw.isBlank()) {
            return snap;
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(raw);

            // Parse dagJson (can be string or object)
            JsonNode dagNode = root.get("dagJson");
            if (dagNode != null) {
                if (dagNode.isTextual()) {
                    dagNode = JsonUtils.getObjectMapper().readTree(dagNode.asText());
                }
                if (dagNode.has("nodes")) {
                    for (JsonNode n : dagNode.get("nodes")) {
                        SnapNode sn = new SnapNode();
                        sn.taskCode = textOf(n, "taskCode");
                        sn.label = textOf(n, "label");
                        if (sn.taskCode == null) {
                            sn.taskCode = sn.label;
                        }
                        snap.nodes.add(sn);
                    }
                }
                if (dagNode.has("edges")) {
                    for (JsonNode e : dagNode.get("edges")) {
                        SnapEdge se = new SnapEdge();
                        se.source = textOf(e, "source");
                        se.target = textOf(e, "target");
                        snap.edges.add(se);
                    }
                }
            } else {
                // Flat format: nodes/edges at root
                if (root.has("nodes")) {
                    for (JsonNode n : root.get("nodes")) {
                        SnapNode sn = new SnapNode();
                        sn.taskCode = textOf(n, "taskCode");
                        sn.label = textOf(n, "label");
                        if (sn.taskCode == null) {
                            sn.taskCode = sn.label;
                        }
                        snap.nodes.add(sn);
                    }
                }
                if (root.has("edges")) {
                    for (JsonNode e : root.get("edges")) {
                        SnapEdge se = new SnapEdge();
                        se.source = textOf(e, "source");
                        se.target = textOf(e, "target");
                        snap.edges.add(se);
                    }
                }
            }

            // Parse taskDefinitions
            JsonNode taskDefs = root.get("taskDefinitions");
            if (taskDefs != null && taskDefs.isArray()) {
                for (JsonNode t : taskDefs) {
                    SnapTask st = new SnapTask();
                    st.code = textOf(t, "code");
                    st.name = textOf(t, "name");
                    st.taskType = textOf(t, "taskType");
                    st.scriptContent = textOf(t, "scriptContent");
                    snap.tasks.add(st);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse workflow snapshot for diff", e);
        }
        return snap;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.isTextual() ? child.asText() : child.toString();
    }

    private static Map<String, SnapNode> toMap(List<SnapNode> nodes) {
        Map<String, SnapNode> map = new LinkedHashMap<>();
        for (SnapNode n : nodes) {
            if (n.taskCode != null) {
                map.put(n.taskCode, n);
            }
        }
        return map;
    }

    private static Map<String, SnapTask> toTaskMap(List<SnapTask> tasks) {
        Map<String, SnapTask> map = new LinkedHashMap<>();
        for (SnapTask t : tasks) {
            if (t.code != null) {
                map.put(t.code, t);
            }
        }
        return map;
    }

    private static Map<String, String> buildLabelMap(List<SnapNode> nodes) {
        Map<String, String> map = new HashMap<>();
        for (SnapNode n : nodes) {
            map.put(n.taskCode, n.label != null ? n.label : n.taskCode);
        }
        return map;
    }

    private static String taskType(Map<String, SnapTask> taskMap, String code) {
        SnapTask t = taskMap.get(code);
        return t != null ? t.taskType : null;
    }

    private static boolean hasContentChanged(SnapTask oldTask, SnapTask newTask) {
        if (oldTask == null || newTask == null) {
            return oldTask != newTask;
        }
        return !Objects.equals(oldTask.scriptContent, newTask.scriptContent);
    }

    // ==================== Internal Data Classes ====================

    private static class Snapshot {

        List<SnapNode> nodes = new ArrayList<>();
        List<SnapEdge> edges = new ArrayList<>();
        List<SnapTask> tasks = new ArrayList<>();
    }

    private static class SnapNode {

        String taskCode;
        String label;
    }

    private static class SnapEdge {

        String source;
        String target;
    }

    private static class SnapTask {

        String code;
        String name;
        String taskType;
        String scriptContent;
    }
}
