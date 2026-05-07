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

package io.github.zzih.rudder.common.annotation;

import io.github.zzih.rudder.common.enums.auth.RoleType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式权限校验注解。
 * <p>
 * 标注在 Controller 方法或类上，PermissionInterceptor 会自动校验
 * 当前用户在所属工作空间中的角色是否 >= 指定的最低角色。
 * <p>
 * 角色层级: SUPER_ADMIN(3) > WORKSPACE_OWNER(2) > DEVELOPER(1) > VIEWER(0)
 * <p>
 * 方法级注解优先于类级注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 最低角色，对应 RoleType 枚举值。
     * 例如 {@code @RequireRole(RoleType.DEVELOPER)} 表示至少需要 DEVELOPER 角色。
     */
    RoleType value();
}
