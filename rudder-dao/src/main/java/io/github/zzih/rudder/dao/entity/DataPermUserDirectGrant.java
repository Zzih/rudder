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
 * 用户的 direct 授权块(不经权限包):一个 scope + 一组操作分组 + 时间窗,挂多条库表行。
 *
 * <p>申请提交时用户自定义的作用域块,审批通过时一次性 INSERT。
 */
@Data
@TableName("t_r_data_perm_user_direct_grant")
public class DataPermUserDirectGrant implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceApprovalId;

    /** 引用 {@code t_r_data_perm_scope.code}。 */
    private Long scopeCode;

    /** 操作分组 id 列表,JSON 数组字符串。 */
    private String groupIds;

    private LocalDateTime effectiveTime;

    private LocalDateTime expirationTime;

    private String endReason;

    private Long endBy;

    private String endNote;
}
