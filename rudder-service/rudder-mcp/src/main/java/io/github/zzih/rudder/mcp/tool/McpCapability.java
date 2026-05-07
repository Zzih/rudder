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

package io.github.zzih.rudder.mcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把 Spring AI {@code @McpTool} 方法关联到一个 Rudder capability id。
 *
 * <p>{@link McpToolGuardAspect} 通过此注解读取目标 capability，触发：
 * <ul>
 *   <li>限流（每 token 配额）</li>
 *   <li>双闸门：scope（token 持有该 capability）+ RBAC（当前角色允许）</li>
 *   <li>审计日志（OK / DENIED_* / ERROR）</li>
 * </ul>
 *
 * <p>用法：
 * <pre>{@code
 * @McpTool(name = "metadata.search", description = "Search tables")
 * @McpCapability("metadata.browse")
 * public List<Table> searchTables(...) { ... }
 * }</pre>
 *
 * <p>capability id 必须在 {@link io.github.zzih.rudder.mcp.capability.CapabilityCatalog} 中存在。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpCapability {

    /** Capability id，参考 {@link io.github.zzih.rudder.mcp.capability.CapabilityIds}。 */
    String value();
}
