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

package io.github.zzih.rudder.publish.api;

import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

/**
 * Publish SPI 入口。Provider 接收业务侧已装配好的 bundle，自行翻译到具体调度器格式后下发。
 * 实现禁止反向依赖 dao / datasource / service。
 */
public interface Publisher extends AutoCloseable {

    String getProvider();

    /** 单工作流发布。 */
    void publishWorkflow(WorkflowPublishBundle bundle);

    /** 项目级批量发布，单次调用包含多个工作流。 */
    void publishProject(ProjectPublishBundle bundle);

    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    @Override
    default void close() {
    }
}
