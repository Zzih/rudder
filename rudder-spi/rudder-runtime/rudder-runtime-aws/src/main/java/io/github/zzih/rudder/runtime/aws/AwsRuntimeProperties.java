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

import lombok.Data;

/**
 * AWS Runtime 配置参数 POJO。
 * 由 {@link AwsRuntimeProvider} 根据平台管理页填入的 params 构造。
 */
@Data
public class AwsRuntimeProperties {

    private String region = "us-east-1";

    private Spark spark = new Spark();
    private Flink flink = new Flink();

    @Data
    public static class Spark {

        /** EMR Serverless Application ID (预先创建好的 Spark application) */
        private String applicationId;
        /** IAM 执行角色 ARN */
        private String executionRoleArn;
    }

    @Data
    public static class Flink {

        /** IAM 服务执行角色 ARN */
        private String serviceExecutionRole;
        /** Flink 代码/artifacts 存储的 S3 路径 */
        private String s3Bucket;
        /** Flink runtime 版本 */
        private String runtimeEnvironment = "FLINK-1_18";
    }
}
