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

package io.github.zzih.rudder.service.workflow.stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ApprovalStageFlowRegistry {

    private final Map<String, ApprovalStageFlow> byResourceType;

    public ApprovalStageFlowRegistry(List<ApprovalStageFlow> flows) {
        this.byResourceType = new HashMap<>();
        for (ApprovalStageFlow flow : flows) {
            ApprovalStageFlow prev = byResourceType.put(flow.resourceType(), flow);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate ApprovalStageFlow for resourceType=" + flow.resourceType()
                                + ": " + prev.getClass().getName() + " vs " + flow.getClass().getName());
            }
        }
    }

    public ApprovalStageFlow require(String resourceType) {
        ApprovalStageFlow flow = byResourceType.get(resourceType);
        if (flow == null) {
            throw new IllegalStateException("No ApprovalStageFlow registered for resourceType=" + resourceType);
        }
        return flow;
    }
}
