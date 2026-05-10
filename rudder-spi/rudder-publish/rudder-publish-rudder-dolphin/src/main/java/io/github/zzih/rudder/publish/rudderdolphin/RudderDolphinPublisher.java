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

package io.github.zzih.rudder.publish.rudderdolphin;

import io.github.zzih.rudder.dolphin.client.RudderDolphinClient;
import io.github.zzih.rudder.publish.api.Publisher;
import io.github.zzih.rudder.publish.api.bundle.ProjectPublishBundle;
import io.github.zzih.rudder.publish.api.bundle.WorkflowPublishBundle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RudderDolphinPublisher implements Publisher {

    private final RudderDolphinClient rudderDolphinClient;

    @Override
    public String getProvider() {
        return RudderDolphinPublisherFactory.PROVIDER;
    }

    @Override
    public void publishWorkflow(WorkflowPublishBundle bundle) {
        log.info("发布工作流到 rudder-dolphin, project={}, workflowCode={}, name={}",
                bundle.getProjectName(),
                bundle.getWorkflow() != null ? bundle.getWorkflow().getCode() : null,
                bundle.getWorkflow() != null ? bundle.getWorkflow().getName() : null);
        // rudderDolphinClient.publishWorkflow(bundle);
    }

    @Override
    public void publishProject(ProjectPublishBundle bundle) {
        log.info("发布项目到 rudder-dolphin, project={}, workflows={}",
                bundle.getProjectName(),
                bundle.getWorkflows() != null ? bundle.getWorkflows().size() : 0);
        // rudderDolphinClient.publishProject(bundle);
    }
}
