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

package io.github.zzih.rudder.service.workflow.controlflow.subworkflow;

import io.github.zzih.rudder.common.param.Property;

import java.util.List;
import java.util.Set;

/**
 * 子工作流执行器接口。
 * <p>
 * 由 workflow 模块实现，controlflow 模块通过此接口调用，
 * 避免 controlflow → workflow 的循环依赖。
 */
public interface SubWorkflowExecutor {

    /**
     * 创建、执行并等待子工作流完成。
     *
     * @param workflowDefinitionCode          子工作流定义 code
     * @param ancestorWorkflowDefinitionCodes 祖先工作流 code 集合(用于循环检测)
     * @param varPool                         传递给子工作流的变量池(父快照,List&lt;Property&gt;)
     * @return 子工作流输出的变量池(全部 Direct.OUT 的 Property),无输出时返回空列表
     * @throws InterruptedException 如果等待被中断
     * @throws RuntimeException     如果子工作流执行失败
     */
    List<Property> executeSubWorkflow(Long workflowDefinitionCode, Set<Long> ancestorWorkflowDefinitionCodes,
                                      List<Property> varPool) throws InterruptedException;
}
