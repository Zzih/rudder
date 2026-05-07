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

package io.github.zzih.rudder.task.http;

import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HTTP 任务参数。调用外部 API / Webhook / 触发下游工作流等场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpTaskParams extends AbstractTaskParams {

    /** 请求 URL,支持 {@code ${var}} / {@code $[time]} 占位符,已由上层 ParameterResolver 渲染。 */
    private String url;

    /** HTTP 方法(GET/POST/PUT/PATCH/DELETE)。默认 GET。 */
    private String method = "GET";

    /** 请求头。 */
    private Map<String, String> headers;

    /** 请求体(POST/PUT/PATCH 使用)。 */
    private String body;

    /** body Content-Type,默认 {@code application/json}(仅当 body 非空时生效)。 */
    private String contentType = "application/json";

    /** 成功 HTTP 状态码列表,响应码不在列表中视为失败。默认 {@code [200]}。 */
    private List<Integer> successCodes = List.of(200);

    /** 连接超时毫秒,默认 10s。 */
    private int connectTimeoutMs = 10_000;

    /** 读取超时毫秒,默认 60s。 */
    private int readTimeoutMs = 60_000;

    /** 失败重试次数,默认 0(不重试)。 */
    private int retries = 0;

    /** 重试间隔毫秒,默认 1s。 */
    private int retryDelayMs = 1_000;

    /** 响应体是否校验包含指定字符串,空则不校验。 */
    private String expectedBodyContains;

    @Override
    public boolean validate() {
        return url != null && !url.isBlank();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.HTTP;
    }
}
