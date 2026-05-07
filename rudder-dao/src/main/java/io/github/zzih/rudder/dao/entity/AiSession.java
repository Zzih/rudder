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

package io.github.zzih.rudder.dao.entity;

import io.github.zzih.rudder.common.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** t_r_ai_session 映射。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_session")
public class AiSession extends BaseEntity {

    private Long workspaceId;

    private String title;

    /** CHAT | AGENT。 */
    private String mode;

    private String modelSnapshot;

    private String systemPromptOverride;

    private Long totalPromptTokens;

    private Long totalCompletionTokens;

    private Long totalCostCents;
}
