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

import lombok.Data;

/**
 * 阿里云 Runtime 配置参数的 POJO 载体。
 * 由 {@link AliyunRuntimeProvider} 根据平台管理页填入的 params 构造。
 */
@Data
public class AliyunRuntimeProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String regionId = "cn-hangzhou";

    private Spark spark = new Spark();
    private Flink flink = new Flink();

    @Data
    public static class Spark {

        private String workspaceId;
        private String resourceQueueId;
    }

    @Data
    public static class Flink {

        private String workspaceId;
        private String namespace = "default";
    }

    /**
     * Serverless Spark SDK endpoint.
     */
    public String sparkEndpoint() {
        return "emr-serverless-spark." + regionId + ".aliyuncs.com";
    }

    /**
     * VVP Flink SDK endpoint.
     */
    public String flinkEndpoint() {
        return "ververica." + regionId + ".aliyuncs.com";
    }
}
