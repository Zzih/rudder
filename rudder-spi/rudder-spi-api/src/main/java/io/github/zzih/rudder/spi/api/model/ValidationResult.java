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

package io.github.zzih.rudder.spi.api.model;

import java.util.List;

/**
 * Factory 配置校验结果。
 *
 * <p>保存配置前由 {@code Factory.validate(config)} 调用，返回字段级错误消息让前端定位问题。
 */
public record ValidationResult(boolean valid, List<FieldError> errors) {

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult fail(String field, String message) {
        return new ValidationResult(false, List.of(new FieldError(field, message)));
    }

    public static ValidationResult fail(List<FieldError> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }

    /** 单个字段的校验错误。{@code field} 为 null 时表示整体校验失败，非字段级。 */
    public record FieldError(String field, String message) {
    }
}
