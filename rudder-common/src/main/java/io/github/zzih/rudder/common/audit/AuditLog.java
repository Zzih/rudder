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

package io.github.zzih.rudder.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要审计的方法。方面在方法执行前后记录：
 * 用户 / IP / HTTP method + URI / module / action / resourceType / 入参 JSON / 耗时 / 状态 / 异常摘要。
 *
 * <p>{@code module} / {@code action} / {@code resourceType} 一律用枚举，禁止字面量字符串，
 * 防止 typo 和同一功能的模块名漂移（新值需要先在对应 enum 里加一项）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    AuditModule module();

    AuditAction action();

    AuditResourceType resourceType() default AuditResourceType.NONE;

    /**
     * SpEL 表达式，指向被操作资源的 numeric id，求值后写入 {@code t_r_audit_log.resource_code}。
     * 常见写法：{@code "#id"} / {@code "#code"} / {@code "#request.scriptCode"}；对 CREATE 场景可用
     * {@code "#result.data.code"} 对返回值求值。空字符串表示不记 resource_code（非资源级动作）。
     */
    String resourceCode() default "";

    /** 供日志里展示的简短描述，可包含接口意图；默认空 */
    String description() default "";
}
