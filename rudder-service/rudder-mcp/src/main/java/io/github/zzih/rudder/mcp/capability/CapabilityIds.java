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

package io.github.zzih.rudder.mcp.capability;

/**
 * Capability ID 常量 — 所有 Tool / Listener / Test 引用 capability 时用这些常量，
 * 避免在 31+ 处硬编码字符串导致 typo 编译期不报。
 *
 * <p>{@code McpToolGuardAspect} 在校验时调 {@code CapabilityCatalog.findById(...)}
 * 校验 catalog 含此 id；常量这一层是双保险，让 typo 在 IDE 阶段就被高亮。
 */
public final class CapabilityIds {

    // workspace
    public static final String WORKSPACE_VIEW = "workspace.view";

    // metadata
    public static final String METADATA_BROWSE = "metadata.browse";

    // datasource
    public static final String DATASOURCE_VIEW = "datasource.view";
    public static final String DATASOURCE_TEST = "datasource.test";
    public static final String DATASOURCE_MANAGE = "datasource.manage";

    // project
    public static final String PROJECT_BROWSE = "project.browse";
    public static final String PROJECT_AUTHOR = "project.author";

    // script
    public static final String SCRIPT_BROWSE = "script.browse";
    public static final String SCRIPT_AUTHOR = "script.author";

    // execution
    public static final String EXECUTION_VIEW_STATUS = "execution.view_status";
    public static final String EXECUTION_VIEW_RESULT = "execution.view_result";
    public static final String EXECUTION_RUN = "execution.run";
    public static final String EXECUTION_CANCEL = "execution.cancel";

    // workflow
    public static final String WORKFLOW_BROWSE = "workflow.browse";
    public static final String WORKFLOW_AUTHOR = "workflow.author";
    public static final String WORKFLOW_RUN = "workflow.run";
    public static final String WORKFLOW_PUBLISH = "workflow.publish";

    // approval
    public static final String APPROVAL_VIEW = "approval.view";
    public static final String APPROVAL_ACT = "approval.act";

    // knowledge base (RAG search)
    public static final String KNOWLEDGE_SEARCH = "knowledge.search";

    private CapabilityIds() {
    }
}
