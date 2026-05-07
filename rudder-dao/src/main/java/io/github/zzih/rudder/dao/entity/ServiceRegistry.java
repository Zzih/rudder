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

import io.github.zzih.rudder.dao.enums.ServiceStatus;
import io.github.zzih.rudder.dao.enums.ServiceType;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_r_service_registry")
public class ServiceRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * SERVER / EXECUTION
     */
    @TableField("`type`")
    private ServiceType type;

    private String host;

    /** RPC 通信端口（内部服务间通信） */
    private Integer port;

    private LocalDateTime startTime;

    private LocalDateTime heartbeat;

    private ServiceStatus status;

    private Integer taskCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
