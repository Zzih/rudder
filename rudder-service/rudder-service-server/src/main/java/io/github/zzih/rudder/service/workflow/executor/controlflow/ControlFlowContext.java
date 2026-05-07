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

package io.github.zzih.rudder.service.workflow.executor.controlflow;

import io.github.zzih.rudder.common.param.Property;
import io.github.zzih.rudder.service.workflow.controlflow.subworkflow.SubWorkflowExecutor;

import java.util.List;
import java.util.Set;

/**
 * 单次控制流节点执行所需的 per-invocation 上下文。
 *
 * <p>varPoolSnapshot 是 {@code List<Property>}(对齐 DS,持 direct/type 信息)。SwitchTask
 * 内部需要 {@code Map<String,String>} 视图给 JS 表达式替换占位符,自行从 list 现转。
 */
public record ControlFlowContext(
        long workflowInstanceId,
        List<Property> varPoolSnapshot,
        Set<Long> ancestorWorkflowDefinitionCodes,
        SubWorkflowExecutor subWorkflowExecutor) {
}
