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

package io.github.zzih.rudder.common.result;

import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.i18n.I18n;

import java.io.Serializable;

import lombok.Data;

/**
 * 统一 HTTP 响应体。
 *
 * <p>{@link ErrorCode#getMessage()} 现在返回 i18n key,所以任何接受 {@code ErrorCode} 的工厂方法
 * 都必须经 {@link I18n#t(String, Object...)} 出口翻译。手动传 {@code message} 字符串的 fail 重载
 * 留给 {@code GlobalExceptionHandler}—— 那里已经调用 {@code resolveMessage} 翻译过了。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setCode(SystemErrorCode.SUCCESS.getCode());
        result.setMessage(I18n.t(SystemErrorCode.SUCCESS.getMessage()));
        return result;
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = ok();
        result.setData(data);
        return result;
    }

    /** {@code message} 必须是已翻译好的文案,handler 路径用。 */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /** 业务直抛 {@link ErrorCode} 的快捷出口,自动用当前线程 locale 翻译 i18n key。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), I18n.t(errorCode.getMessage()));
    }
}
