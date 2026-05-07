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

package io.github.zzih.rudder.service.workflow.controlflow;

import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

/**
 * 控制流任务基类。
 * <p>
 * 提供统一的生命周期管理（init / handle / cancel / getStatus），
 * 以及分支决策的存取。子类只需实现 {@link #handle()} 即可。
 */
@Slf4j
public abstract class AbstractControlFlowTask extends AbstractTask {

    protected volatile TaskStatus status = TaskStatus.SUBMITTED;
    private BranchDecision branchDecision;

    @Override
    public void init() throws TaskException {
        status = TaskStatus.RUNNING;
    }

    @Override
    public void cancel() throws TaskException {
        status = TaskStatus.CANCELLED;
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }

    /** 获取分支决策（CONDITION / SWITCH 产生），无分支的任务返回 null */
    public BranchDecision getBranchDecision() {
        return branchDecision;
    }

    protected void setBranchDecision(BranchDecision decision) {
        this.branchDecision = decision;
    }

    protected Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        return JsonUtils.fromJson(configJson, new TypeReference<>() {
        });
    }
}
