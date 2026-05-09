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

package io.github.zzih.rudder.approval.kissflow;

import java.util.Map;

public record KissflowApprovalProperties(
        String apiKey,
        String accountId,
        String processId,
        String titleField,
        String contentField,
        String applicantField,
        Map<String, String> stageFieldMapping) {

    public KissflowApprovalProperties {
        if (apiKey == null) {
            apiKey = "";
        }
        if (accountId == null) {
            accountId = "";
        }
        if (processId == null) {
            processId = "";
        }
        if (titleField == null || titleField.isBlank()) {
            titleField = "Title";
        }
        if (contentField == null || contentField.isBlank()) {
            contentField = "Description";
        }
        if (applicantField == null) {
            applicantField = "";
        }
        if (stageFieldMapping == null) {
            stageFieldMapping = Map.of();
        }
    }
}
