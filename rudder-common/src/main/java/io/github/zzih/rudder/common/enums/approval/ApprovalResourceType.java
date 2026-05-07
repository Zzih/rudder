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

package io.github.zzih.rudder.common.enums.approval;

/**
 * 审批资源类型常量。t_r_approval_record.resource_type 字段使用字符串存储以便扩展。
 * 新增审批类型时在此处加常量，并新建对应的 ApprovalStageFlow + ApproverResolver。
 */
public final class ApprovalResourceType {

    public static final String PROJECT_PUBLISH = "PROJECT_PUBLISH";
    public static final String WORKFLOW_PUBLISH = "WORKFLOW_PUBLISH";
    public static final String MCP_TOKEN = "MCP_TOKEN";

    private ApprovalResourceType() {
    }
}
