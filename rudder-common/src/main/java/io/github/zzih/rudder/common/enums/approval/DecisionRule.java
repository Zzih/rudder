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
 * 决议规则：决定该审批阶段需要多少个候选人 APPROVE 才能推进。
 *
 * <p>与 {@code ApprovalRecord.requiredCount} 字段配合：
 * <ul>
 *   <li>{@link #ANY_1} — 任一候选人通过即推进，required_count 固定为 1</li>
 *   <li>{@link #N_OF_M} — 至少 N 个候选人 APPROVE 才推进，required_count = N</li>
 *   <li>{@link #ALL} — 所有候选人都需要 APPROVE，required_count = 候选人总数（运行时确定）</li>
 * </ul>
 */
public enum DecisionRule {

    ANY_1,
    N_OF_M,
    ALL;

    /** ANY_1 / ALL 的 required_count 默认值；N_OF_M 必须由调用方显式提供 N。 */
    public int defaultRequiredCount() {
        return this == ANY_1 ? 1 : 0;
    }
}
