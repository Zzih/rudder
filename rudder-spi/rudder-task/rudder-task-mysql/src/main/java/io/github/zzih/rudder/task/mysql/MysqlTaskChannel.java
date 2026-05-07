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

package io.github.zzih.rudder.task.mysql;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.task.api.context.TaskExecutionContext;
import io.github.zzih.rudder.task.api.params.AbstractTaskParams;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.spi.TaskChannel;
import io.github.zzih.rudder.task.api.task.AbstractTask;

public class MysqlTaskChannel implements TaskChannel {

    @Override
    public AbstractTask createTask(TaskExecutionContext ctx) {
        SqlTaskParams params = JsonUtils.fromJson(ctx.getParamsJson(), SqlTaskParams.class);
        return new MysqlTask(ctx, params);
    }

    @Override
    public AbstractTaskParams parseParams(String paramsJson) {
        return JsonUtils.fromJson(paramsJson, SqlTaskParams.class);
    }
}
