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

package io.github.zzih.rudder.service.workflow.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.entity.Script;
import io.github.zzih.rudder.dao.entity.TaskDefinition;
import io.github.zzih.rudder.dao.entity.TaskInstance;
import io.github.zzih.rudder.dao.entity.WorkflowInstance;
import io.github.zzih.rudder.dao.enums.InstanceStatus;
import io.github.zzih.rudder.dao.enums.SourceType;
import io.github.zzih.rudder.service.workflow.dag.DagNode;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskInstanceFactoryTest {

    @Mock
    private ScriptDao scriptDao;

    private TaskInstanceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TaskInstanceFactory(scriptDao);
    }

    @Test
    @DisplayName("buildForNode 从 taskDef/node/instance 复制快照字段")
    void buildForNode_snapshotFields() {
        DagNode node = node(101L, "ingest-users");
        TaskDefinition def = taskDef(101L, TaskType.MYSQL, null);
        WorkflowInstance instance = instance(7L, 42L, 3L);

        TaskInstance task = factory.buildForNode(node, def, InstanceStatus.PENDING, instance);

        assertThat(task.getTaskType()).isEqualTo(TaskType.MYSQL);
        assertThat(task.getSourceType()).isEqualTo(SourceType.TASK);
        assertThat(task.getWorkspaceId()).isEqualTo(3L);
        assertThat(task.getWorkflowInstanceId()).isEqualTo(7L);
        assertThat(task.getTaskDefinitionCode()).isEqualTo(101L);
        assertThat(task.getStatus()).isEqualTo(InstanceStatus.PENDING);
        assertThat(task.getName()).startsWith("ingest-users_");
    }

    @Test
    @DisplayName("buildForNode 在 scriptCode 存在时加载脚本内容")
    void buildForNode_loadsScriptContent() {
        DagNode node = node(101L, "label");
        TaskDefinition def = taskDef(101L, TaskType.MYSQL, 555L);
        Script script = new Script();
        script.setCode(555L);
        script.setContent("SELECT 1");
        when(scriptDao.selectByCode(555L)).thenReturn(script);

        TaskInstance task = factory.buildForNode(node, def, InstanceStatus.PENDING, instance(7L, 42L, 3L));

        assertThat(task.getContent()).isEqualTo("SELECT 1");
        assertThat(task.getScriptCode()).isEqualTo(555L);
    }

    @Test
    @DisplayName("buildSkipped 标记 SKIPPED 状态且 startedAt = finishedAt")
    void buildSkipped_timestamps() {
        TaskInstance task = factory.buildSkipped(
                node(1L, "n"), taskDef(1L, TaskType.MYSQL, null), instance(7L, 42L, 3L));

        assertThat(task.getStatus()).isEqualTo(InstanceStatus.SKIPPED);
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getFinishedAt()).isEqualTo(task.getStartedAt());
    }

    @Test
    @DisplayName("buildUpstreamFailed 写入错误信息并置为 FAILED")
    void buildUpstreamFailed_errorMessage() {
        TaskInstance task = factory.buildUpstreamFailed(
                node(1L, "n"), taskDef(1L, TaskType.MYSQL, null), instance(7L, 42L, 3L));

        assertThat(task.getStatus()).isEqualTo(InstanceStatus.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("Upstream task failed");
        assertThat(task.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("logPath 按 instance+task 字段生成路径")
    void logPath_includesInstanceAndTaskFields() {
        TaskInstance task = new TaskInstance();
        task.setId(9001L);
        task.setName("n_20260418_120000_001");

        String path = factory.logPath(task, instance(7L, 42L, 3L), "ws-default");

        assertThat(path).contains("ws-default");
        assertThat(path).contains("42"); // workflowDefinitionCode
        assertThat(path).contains("7"); // workflowInstanceId
        assertThat(path).contains("9001"); // taskInstance id
    }

    private static DagNode node(Long code, String label) {
        DagNode n = new DagNode();
        n.setTaskCode(code);
        n.setLabel(label);
        return n;
    }

    private static TaskDefinition taskDef(Long code, TaskType type, Long scriptCode) {
        TaskDefinition def = new TaskDefinition();
        def.setCode(code);
        def.setName("def-" + code);
        def.setTaskType(type);
        def.setScriptCode(scriptCode);
        return def;
    }

    private static WorkflowInstance instance(Long id, Long workflowDefinitionCode, Long workspaceId) {
        WorkflowInstance inst = new WorkflowInstance();
        inst.setId(id);
        inst.setWorkflowDefinitionCode(workflowDefinitionCode);
        inst.setWorkspaceId(workspaceId);
        return inst;
    }
}
