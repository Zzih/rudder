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

package io.github.zzih.rudder.approval.api.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalRequest {

    private String title;

    private String content;

    /**
     * 扁平化全部审批人 username 列表（兼容旧调用方 / 简单实现）。
     * 优先使用 {@link #stageCandidates} 携带分阶段信息。
     */
    private List<String> approvers;

    /** 申请人用户名 */
    private String applicantUsername;

    /** 申请人邮箱 — 外部审批渠道用此反查 open_id / 飞书账号 */
    private String applicantEmail;

    /**
     * 各阶段候选审批人邮箱列表。
     * key = 阶段名（如 PROJECT_OWNER / WORKSPACE_OWNER）
     * value = 该阶段候选人邮箱列表
     *
     * <p>外部审批渠道（LARK/KISSFLOW）按此 map 把候选人填到模板对应的人员控件，
     * 模板内部按阶段路由审批。
     */
    private Map<String, List<String>> stageCandidates;

    private String callbackUrl;

    private Map<String, String> extra;
}
