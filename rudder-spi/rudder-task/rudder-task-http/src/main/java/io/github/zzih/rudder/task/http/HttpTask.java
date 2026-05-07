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

import io.github.zzih.rudder.common.enums.error.TaskErrorCode;
import io.github.zzih.rudder.common.exception.TaskException;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.task.AbstractTask;
import io.github.zzih.rudder.task.api.task.LocalTask;
import io.github.zzih.rudder.task.api.task.enums.TaskStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 任务 —— 发起一次 HTTP 调用(触发外部 API / Webhook / 下游系统)。
 * <p>
 * 用 JDK 内置 {@link HttpClient},无额外依赖。支持重试、状态码白名单、响应体断言。
 * 响应码不在 {@code successCodes} 列表中视为失败,响应体校验失败也视为失败。
 */
@Slf4j
public class HttpTask extends AbstractTask implements LocalTask {

    private final HttpTaskParams params;
    private volatile TaskStatus status = TaskStatus.SUBMITTED;
    private volatile HttpClient client;

    public HttpTask(TaskExecutionContext ctx, HttpTaskParams params) {
        super(ctx);
        this.params = params;
    }

    @Override
    public void init() throws TaskException {
        if (!params.validate()) {
            throw new TaskException(TaskErrorCode.TASK_HTTP_URL_REQUIRED);
        }
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(params.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        status = TaskStatus.RUNNING;
    }

    @Override
    public void handle() throws TaskException {
        log.info("HTTP {} {}", params.getMethod(), params.getUrl());
        Exception lastError = null;
        int attempts = Math.max(1, params.getRetries() + 1);
        for (int i = 0; i < attempts; i++) {
            if (status == TaskStatus.CANCELLED) {
                return;
            }
            try {
                HttpResponse<String> response = doSend();
                int code = response.statusCode();
                String body = response.body();
                log.info("Response: status={}, body-len={}", code, body == null ? 0 : body.length());

                if (!params.getSuccessCodes().contains(code)) {
                    throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                            "HTTP status " + code + " not in successCodes=" + params.getSuccessCodes());
                }
                if (params.getExpectedBodyContains() != null && !params.getExpectedBodyContains().isBlank()
                        && (body == null || !body.contains(params.getExpectedBodyContains()))) {
                    throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED,
                            "Response body missing expected substring: " + params.getExpectedBodyContains());
                }
                status = TaskStatus.SUCCESS;
                log.info("Status → SUCCESS");
                return;
            } catch (TaskException e) {
                lastError = e;
                log.warn("Attempt {}/{} failed: {}", i + 1, attempts, e.getMessage());
            } catch (Exception e) {
                lastError = e;
                log.warn("Attempt {}/{} failed: {}", i + 1, attempts, e.getMessage());
            }
            if (i < attempts - 1) {
                try {
                    Thread.sleep(params.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        status = TaskStatus.FAILED;
        log.info("Status → FAILED");
        String msg = lastError == null ? "HTTP task failed" : lastError.getMessage();
        throw new TaskException(TaskErrorCode.TASK_EXECUTE_FAILED, msg);
    }

    private HttpResponse<String> doSend() throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(params.getUrl()))
                .timeout(Duration.ofMillis(params.getReadTimeoutMs()));
        String method = params.getMethod() == null ? "GET" : params.getMethod().toUpperCase();
        HttpRequest.BodyPublisher bodyPub = params.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(params.getBody());
        rb.method(method, bodyPub);
        if (params.getBody() != null && params.getContentType() != null) {
            rb.header("Content-Type", params.getContentType());
        }
        if (params.getHeaders() != null) {
            params.getHeaders().forEach(rb::header);
        }
        return client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void cancel() throws TaskException {
        status = TaskStatus.CANCELLED;
        // JDK HttpClient 没有主动 abort API;状态置 CANCELLED 让 retry loop 停下,
        // 正在进行的 send() 由 readTimeout 兜底结束。
    }

    @Override
    public TaskStatus getStatus() {
        return status;
    }
}
