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

package io.github.zzih.rudder.api.advice;

import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.RudderException;
import io.github.zzih.rudder.common.result.ErrorCode;
import io.github.zzih.rudder.common.result.Result;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常 → JSON Result。
 *
 * <p>所有业务异常归一到 {@link RudderException};HTTP status 由 {@code errorCode.getCode()} 推导
 * (200/4xx/5xx 直当 HTTP code,业务码 1000+ 统一 200,客户端读 Result.code)。
 *
 * <p>SSE endpoint({@code produces = "text/event-stream"})在 handler 前抛异常时 MVC 会沿用 SSE
 * Content-Type 找不到 {@code Result} 的 converter → 500。这里统一返回 {@link ResponseEntity} 并显式
 * {@code .contentType(APPLICATION_JSON)} 覆写,保证错误响应永远 JSON。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static <T> ResponseEntity<Result<T>> json(HttpStatus status, Result<T> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /**
     * HTTP status 派发:errorCode 在 [200, 600) 当 HTTP code 用,其它(业务码 1000+)统一 200,
     * 客户端从 {@code Result.code} 字段读具体业务错误号。
     */
    private static HttpStatus toHttpStatus(ErrorCode code) {
        int c = code.getCode();
        if (c >= 200 && c < 600) {
            try {
                return HttpStatus.valueOf(c);
            } catch (IllegalArgumentException ignored) {
                // 落到默认 200
            }
        }
        return HttpStatus.OK;
    }

    @ExceptionHandler(RudderException.class)
    public ResponseEntity<Result<Void>> handleRudder(RudderException e) {
        ErrorCode code = e.getErrorCode();
        HttpStatus status = toHttpStatus(code);
        String msg = e.getLocalizedMessage();
        // 仅 5xx 打 ERROR + 堆栈;4xx 与业务码段(1000+ 映射到 200) 走 WARN 不带堆栈,避免高频校验/限流刷屏
        if (status.is5xxServerError()) {
            log.error("Rudder exception: code={}, status={}, message={}", code.getCode(), status.value(), msg, e);
        } else {
            log.warn("Rudder exception: code={}, status={}, message={}", code.getCode(), status.value(), msg);
        }
        return json(status, Result.fail(code.getCode(), msg));
    }

    /**
     * Spring 内部 fluent throw 的 HTTP 异常(如 {@code requireXxx} 抛 503/401)。透传 status,
     * 不要落到 {@link #handleException} 被当成 INTERNAL_SERVER_ERROR + 堆栈日志。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatusException(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String msg = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            log.warn("HTTP {}: {}", status.value(), msg);
        } else {
            log.info("HTTP {}: {}", status.value(), msg);
        }
        return json(status, Result.fail(status.value(), msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return json(HttpStatus.BAD_REQUEST, Result.fail(SystemErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR,
                Result.fail(SystemErrorCode.INTERNAL_ERROR.getCode(), "Internal server error"));
    }
}
