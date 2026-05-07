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

/**
 * 把作业提交到外部集群(YARN / Flink Cluster / 等)运行的 Task。Worker 拿这两个指针把
 * instance 标到集群侧:appId 用于 cancel / 状态查询,trackingUrl 用于 UI 跳转。
 * 流式作业 {@code isStreaming() == true},handle() 提交完返回,instance 保持 RUNNING
 * 直到集群侧推送终态。
 *
 * <p>普通 SQL / Shell / HTTP 这些不挂集群的 Task 不实现这个接口。
 */
public interface JobTask extends Task {

    /** 集群作业 ID(YARN application_xxx / Flink jobId / Spark statementId 等)。 */
    String getAppId();

    /** 集群 UI 跳转链接(YARN ResourceManager / Flink Dashboard / Spark UI)。 */
    default String getTrackingUrl() {
        return null;
    }

    /** 流式作业。handle() 返回后 instance 标 RUNNING,后续靠 {@link #getAppId()} 跟踪。 */
    default boolean isStreaming() {
        return false;
    }
}
