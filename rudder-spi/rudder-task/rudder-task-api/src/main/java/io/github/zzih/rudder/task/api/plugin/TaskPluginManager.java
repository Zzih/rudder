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

package io.github.zzih.rudder.task.api.plugin;

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.spi.api.AbstractPluginRegistry;
import io.github.zzih.rudder.spi.api.model.PluginProviderDefinition;
import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.spi.TaskChannel;
import io.github.zzih.rudder.task.api.spi.TaskChannelFactory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class TaskPluginManager extends AbstractPluginRegistry<TaskType, TaskChannelFactory> {

    /** TaskChannel 是无参 SPI，没有用户配置项；providerDefinitions 中 params / guide 为空，仅列出支持的 TaskType。 */
    private Map<TaskType, PluginProviderDefinition> providerDefinitionsCache = Map.of();

    public TaskPluginManager() {
        super(TaskChannelFactory.class);
    }

    @Override
    protected TaskType keyOf(TaskChannelFactory factory) {
        return factory.getTaskType();
    }

    @Override
    protected void onAfterInit() {
        Map<TaskType, PluginProviderDefinition> defs = new LinkedHashMap<>();
        factories.forEach((type, factory) -> defs.put(type, new PluginProviderDefinition(
                List.of(), "", factory.metadata(), factory.priority(), false)));
        providerDefinitionsCache = Collections.unmodifiableMap(defs);
    }

    /**
     * 返回所有已注册的 TaskType 及其定义，供前端展示下拉选项等。TaskChannel 无参数 / 无接入指南，
     * 所以 value 只是一个同构的空定义，真正有用的信息是 keySet。
     */
    public Map<TaskType, PluginProviderDefinition> getProviderDefinitions() {
        return providerDefinitionsCache;
    }

    /**
     * 根据给定的任务类型返回一个新的 {@link TaskChannel}。
     *
     * @throws TaskException 如果没有为该任务类型注册工厂
     */
    public TaskChannel getChannel(TaskType taskType) {
        TaskChannelFactory factory = factories.get(taskType);
        if (factory == null) {
            throw new TaskException(TaskErrorCode.TASK_TYPE_NOT_FOUND,
                    "No task channel factory found for type: " + taskType);
        }
        return factory.create();
    }

    public AbstractTaskParams parseParams(TaskType taskType, String paramsJson) {
        return getChannel(taskType).parseParams(paramsJson);
    }
}
