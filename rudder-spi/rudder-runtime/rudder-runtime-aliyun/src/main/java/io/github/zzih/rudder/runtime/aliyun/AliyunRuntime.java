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

package io.github.zzih.rudder.runtime.aliyun;

import io.github.zzih.rudder.runtime.aliyun.flink.AliyunFlinkJarTask;
import io.github.zzih.rudder.runtime.aliyun.flink.AliyunFlinkSqlTask;
import io.github.zzih.rudder.runtime.aliyun.spark.AliyunSparkJarTask;
import io.github.zzih.rudder.runtime.aliyun.spark.AliyunSparkSqlTask;
import io.github.zzih.rudder.runtime.api.AbstractEngineRuntime;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTaskParams;
import io.github.zzih.rudder.task.spark.jar.SparkJarTaskParams;

import java.util.List;
import java.util.Map;

/**
 * 阿里云 runtime —— 接管 Spark/Flink 的 SQL/JAR 4 种 TaskType。
 * 接管方式 = 替换 Task 实例为对应的 {@code Aliyun*Task} 子类(extends 原生 Task)。
 *
 * <p>其它 TaskType 走 channel 默认工厂(Shell/Python/MySQL SQL 等),与 Local 无差。
 */
public class AliyunRuntime extends AbstractEngineRuntime {

    public static final String PROVIDER_KEY = "ALIYUN";

    public AliyunRuntime(AliyunRuntimeProperties props,
                         Map<String, String> envVars,
                         com.aliyun.ververica20220718.Client vvpClient,
                         com.aliyun.emr_serverless_spark20230808.Client sparkClient) {
        super(PROVIDER_KEY, envVars, List.of(
                bind(TaskType.SPARK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new AliyunSparkSqlTask(ctx, p, props, sparkClient)),
                bind(TaskType.SPARK_JAR, SparkJarTaskParams.class,
                        (ctx, p) -> new AliyunSparkJarTask(ctx, p, props, sparkClient)),
                bind(TaskType.FLINK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new AliyunFlinkSqlTask(ctx, p, props, vvpClient)),
                bind(TaskType.FLINK_JAR, FlinkJarTaskParams.class,
                        (ctx, p) -> new AliyunFlinkJarTask(ctx, p, props, vvpClient))));
    }
}
