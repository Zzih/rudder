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

package io.github.zzih.rudder.task.api.spi;

import io.github.zzih.rudder.spi.api.PluginProviderFactory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

/**
 * SPI 入口点，通过 {@link java.util.ServiceLoader} 加载。
 */
public interface TaskChannelFactory extends PluginProviderFactory {

    /**
     * 返回此工厂处理的任务类型。
     */
    TaskType getTaskType();

    /**
     * 创建一个新的 {@link TaskChannel} 实例。
     */
    TaskChannel create();
}
