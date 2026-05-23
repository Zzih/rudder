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

package io.github.zzih.rudder.task.api.task;

import io.github.zzih.rudder.common.sql.TableAccess;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;

import java.util.List;

/**
 * 受数据权限管控的任务能力声明。Worker pipeline 仅对实现此接口的 task 跑 Local 鉴权:
 * 拿 {@code ctx.getTaskType()} 反查 Scope ({@code Scope.managedTaskTypes} 包含该 TaskType 即命中),
 * 用 {@link #resolveAccessIntent(TaskExecutionContext)} 产出的 intents 比对 user 权限快照,缺权拒绝执行。
 *
 * <p>实现示例:JDBC 类 SQL 任务({@code AbstractJdbcSqlTask})自带 sql 文本,直接实现。
 * Spark Jar / Flink Jar / Shell 等 Worker 拿不到访问意图的任务**不应实现此接口**,由 Ranger 同步
 * 或外部机制在引擎侧兜底。
 */
public interface DataPermAwareTask extends Task {

    /** 任务自我解析的访问意图。空 list = 任务不访问任何受管资源,放行。 */
    List<TableAccess> resolveAccessIntent(TaskExecutionContext ctx);
}
