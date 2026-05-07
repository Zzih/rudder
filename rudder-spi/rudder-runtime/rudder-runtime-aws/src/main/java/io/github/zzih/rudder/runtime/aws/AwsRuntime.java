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

package io.github.zzih.rudder.runtime.aws;

import io.github.zzih.rudder.runtime.api.AbstractEngineRuntime;
import io.github.zzih.rudder.runtime.aws.flink.AwsFlinkJarTask;
import io.github.zzih.rudder.runtime.aws.flink.AwsFlinkSqlTask;
import io.github.zzih.rudder.runtime.aws.spark.AwsSparkJarTask;
import io.github.zzih.rudder.runtime.aws.spark.AwsSparkSqlTask;
import io.github.zzih.rudder.task.api.params.SqlTaskParams;
import io.github.zzih.rudder.task.api.task.enums.TaskType;
import io.github.zzih.rudder.task.flink.jar.FlinkJarTaskParams;
import io.github.zzih.rudder.task.spark.jar.SparkJarTaskParams;

import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.emrserverless.EmrServerlessClient;
import software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2Client;

/**
 * AWS runtime —— EMR Serverless (Spark SQL/JAR) + Managed Flink (Flink SQL/JAR)。
 * 接管方式 = 替换 Task 实例为对应的 {@code Aws*Task} 子类(extends 原生 Task)。
 */
public class AwsRuntime extends AbstractEngineRuntime {

    public static final String PROVIDER_KEY = "AWS";

    public AwsRuntime(AwsRuntimeProperties props,
                      Map<String, String> envVars,
                      EmrServerlessClient emrClient,
                      KinesisAnalyticsV2Client flinkClient) {
        super(PROVIDER_KEY, envVars, List.of(
                bind(TaskType.SPARK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new AwsSparkSqlTask(ctx, p, props, emrClient)),
                bind(TaskType.SPARK_JAR, SparkJarTaskParams.class,
                        (ctx, p) -> new AwsSparkJarTask(ctx, p, props, emrClient)),
                bind(TaskType.FLINK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new AwsFlinkSqlTask(ctx, p, props, flinkClient)),
                bind(TaskType.FLINK_JAR, FlinkJarTaskParams.class,
                        (ctx, p) -> new AwsFlinkJarTask(ctx, p, props, flinkClient))));
    }
}
