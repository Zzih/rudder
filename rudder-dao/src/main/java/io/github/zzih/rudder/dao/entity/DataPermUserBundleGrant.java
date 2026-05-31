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

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户被授予的资源包(role grant)。
 *
 * <p>RBAC 语义:仅引用 bundle_id,实际权限项内容现场 JOIN 权限包的作用域块当前态(自动跟随包变化)。
 *
 * <p>生命周期:
 * <ul>
 *   <li>{@code expiration_time = NULL} → 永久</li>
 *   <li>{@code expiration_time <= now()} → 已失效</li>
 *   <li>{@code end_reason} 区分自然到期({@code EXPIRED})/管理员撤销({@code REVOKED})/
 *       删包级联失效({@code ROLE_DELETED})</li>
 * </ul>
 */
@Data
@TableName("t_r_data_perm_user_bundle_grant")
public class DataPermUserBundleGrant implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceApprovalId;

    private Long bundleId;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;

    private String endReason;

    private Long endBy;

    private String endNote;
}
