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

package io.github.zzih.rudder.runtime.aliyun.flink;

import io.github.zzih.rudder.common.utils.process.PollingUtils;
import io.github.zzih.rudder.runtime.aliyun.AliyunRuntimeProperties;

import java.time.Duration;

import com.aliyun.ververica20220718.Client;
import com.aliyun.ververica20220718.models.CreateSavepointHeaders;
import com.aliyun.ververica20220718.models.CreateSavepointRequest;
import com.aliyun.ververica20220718.models.CreateSavepointResponse;
import com.aliyun.ververica20220718.models.GetSavepointHeaders;
import com.aliyun.ververica20220718.models.GetSavepointResponse;
import com.aliyun.ververica20220718.models.ListJobsHeaders;
import com.aliyun.ververica20220718.models.ListJobsRequest;
import com.aliyun.ververica20220718.models.ListJobsResponse;
import com.aliyun.ververica20220718.models.StopJobHeaders;
import com.aliyun.ververica20220718.models.StopJobRequest;
import com.aliyun.ververica20220718.models.StopJobRequestBody;

import lombok.extern.slf4j.Slf4j;

/**
 * VVP Savepoint 操作（基于 ververica SDK）。
 */
@Slf4j
final class VvpSavepointUtils {

    private VvpSavepointUtils() {
    }

    /**
     * 触发 savepoint，轮询直到完成，返回 savepoint 路径。
     */
    static String triggerSavepoint(AliyunRuntimeProperties props, Client vvpClient, String deploymentId) {
        String ns = props.getFlink().getNamespace();

        try {
            CreateSavepointRequest req = new CreateSavepointRequest()
                    .setDeploymentId(deploymentId)
                    .setDescription("rudder-triggered");
            CreateSavepointHeaders headers = new CreateSavepointHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());

            log.info("Triggering VVP savepoint for deployment: {}", deploymentId);
            CreateSavepointResponse resp = vvpClient.createSavepointWithOptions(
                    ns, req, headers, new com.aliyun.teautil.models.RuntimeOptions());

            var body = resp.getBody();
            if (body == null || body.getData() == null || body.getData().getSavepointId() == null) {
                throw new RuntimeException("Failed to trigger savepoint, no savepointId returned");
            }
            String savepointId = body.getData().getSavepointId();

            return pollSavepointStatus(props, vvpClient, ns, savepointId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to trigger savepoint: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 StopJob(STOP_WITH_SAVEPOINT) 停止作业并生成 savepoint。
     */
    static String stopWithSavepoint(AliyunRuntimeProperties props, Client vvpClient, String deploymentId) {
        String ns = props.getFlink().getNamespace();

        try {
            // 先查 jobId
            ListJobsRequest listReq = new ListJobsRequest().setDeploymentId(deploymentId);
            ListJobsHeaders listHeaders = new ListJobsHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());
            ListJobsResponse jobsResp = vvpClient.listJobsWithOptions(
                    ns, listReq, listHeaders, new com.aliyun.teautil.models.RuntimeOptions());

            if (jobsResp.getBody().getData() == null || jobsResp.getBody().getData().isEmpty()) {
                throw new RuntimeException("No active job found for deployment " + deploymentId);
            }
            String jobId = jobsResp.getBody().getData().get(0).getJobId();

            log.info("Stopping job {} with savepoint (deployment: {})", jobId, deploymentId);
            StopJobRequestBody stopBody = new StopJobRequestBody()
                    .setStopStrategy("STOP_WITH_SAVEPOINT");
            StopJobRequest stopReq = new StopJobRequest().setBody(stopBody);
            StopJobHeaders stopHeaders = new StopJobHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());
            vvpClient.stopJobWithOptions(ns, jobId, stopReq, stopHeaders,
                    new com.aliyun.teautil.models.RuntimeOptions());

            log.info("Stop-with-savepoint triggered for job {}", jobId);
            return "savepoint triggered via STOP_WITH_SAVEPOINT for job " + jobId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop with savepoint: " + e.getMessage(), e);
        }
    }

    /**
     * 查询 deployment 下最新 Job 的状态。
     */
    static String queryCurrentJobStatus(AliyunRuntimeProperties props, Client vvpClient, String deploymentId) {
        try {
            String ns = props.getFlink().getNamespace();
            ListJobsRequest listReq = new ListJobsRequest().setDeploymentId(deploymentId);
            ListJobsHeaders listHeaders = new ListJobsHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());
            ListJobsResponse jobsResp = vvpClient.listJobsWithOptions(
                    ns, listReq, listHeaders, new com.aliyun.teautil.models.RuntimeOptions());
            var jobs = jobsResp.getBody().getData();
            if (jobs == null || jobs.isEmpty()) {
                return null;
            }
            var jobStatus = jobs.get(0).getStatus();
            return jobStatus != null ? jobStatus.getCurrentJobStatus() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取消 deployment（ListJobs → StopJob）。
     */
    static void cancelDeployment(AliyunRuntimeProperties props, Client vvpClient, String deploymentId) {
        try {
            String ns = props.getFlink().getNamespace();
            ListJobsRequest listReq = new ListJobsRequest().setDeploymentId(deploymentId);
            ListJobsHeaders listHeaders = new ListJobsHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());
            ListJobsResponse jobsResp = vvpClient.listJobsWithOptions(
                    ns, listReq, listHeaders, new com.aliyun.teautil.models.RuntimeOptions());
            var jobs = jobsResp.getBody().getData();
            if (jobs == null || jobs.isEmpty()) {
                return;
            }

            String jobId = jobs.get(0).getJobId();
            StopJobRequestBody stopBody = new StopJobRequestBody().setStopStrategy("NONE");
            StopJobRequest stopReq = new StopJobRequest().setBody(stopBody);
            StopJobHeaders stopHeaders = new StopJobHeaders()
                    .setWorkspace(props.getFlink().getWorkspaceId());
            vvpClient.stopJobWithOptions(ns, jobId, stopReq, stopHeaders,
                    new com.aliyun.teautil.models.RuntimeOptions());
        } catch (Exception e) {
            // log handled by caller
        }
    }

    private static String pollSavepointStatus(AliyunRuntimeProperties props, Client vvpClient,
                                              String ns, String savepointId) {
        GetSavepointHeaders headers = new GetSavepointHeaders()
                .setWorkspace(props.getFlink().getWorkspaceId());

        return PollingUtils.poll(
                () -> {
                    try {
                        GetSavepointResponse resp = vvpClient.getSavepointWithOptions(
                                ns, savepointId, headers, new com.aliyun.teautil.models.RuntimeOptions());
                        return resp.getBody().getData();
                    } catch (Exception e) {
                        log.warn("Error polling savepoint: {}", e.getMessage());
                        return null;
                    }
                },
                savepoint -> {
                    if (savepoint == null) {
                        return null;
                    }
                    String state = savepoint.getStatus() != null ? savepoint.getStatus().getState() : null;
                    if ("COMPLETED".equalsIgnoreCase(state)) {
                        String location = savepoint.getSavepointLocation();
                        log.info("VVP savepoint completed: {}", location);
                        return location;
                    }
                    if ("FAILED".equalsIgnoreCase(state)) {
                        throw new RuntimeException("VVP savepoint failed for " + savepointId);
                    }
                    return null;
                },
                Duration.ofSeconds(1), 120,
                "VVP savepoint timed out after 120s");
    }
}
