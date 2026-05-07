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

package io.github.zzih.rudder.task.api.params;

import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public abstract class AbstractTaskParams {

    private List<TaskParam> inputParams;
    private List<TaskParam> outputParams;

    /**
     * 校验参数。
     *
     * @return 参数有效时返回 {@code true}
     */
    public abstract boolean validate();

    /**
     * 返回此参数对象所属的任务类型。
     */
    public abstract TaskType getTaskType();

    /**
     * 返回需要从 FileStorage 下载的资源文件列表。
     * key 为参数名（如 "jarPath"），value 为 FileStorage 中的相对路径。
     * 默认返回空 Map，子类按需重写。
     *
     * <p>TaskWorker 在执行前统一下载这些文件到本地工作目录，
     * 并将解析后的本地路径回写到 {@code TaskExecutionContext.resolvedFilePaths}。
     * Task 实现通过 {@code ctx.getResolvedFilePaths().get("jarPath")} 获取本地路径。
     */
    public Map<String, String> getResourceFiles() {
        return Map.of();
    }
}
