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

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** t_r_ai_mcp_server 映射。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_mcp_server")
public class AiMcpServer extends BaseEntity {

    private String name;

    /** STDIO | HTTP_SSE。 */
    private String transport;

    private String command;

    private String url;

    /** JSON,启动环境变量。 */
    private String env;

    /** 加密存储。 */
    private String credentials;

    /** JSON 数组,null=全允许。 */
    private String toolAllowlist;

    /** UP | DOWN | UNKNOWN。 */
    private String healthStatus;

    private LocalDateTime lastHealthAt;

    private Boolean enabled;
}
