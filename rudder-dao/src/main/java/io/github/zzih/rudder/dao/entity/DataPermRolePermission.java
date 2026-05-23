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

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 资源包内权限项当前态。
 *
 * <p>每条记录 = (Ranger Service, 资源定位四元组, accesses)。catalog/db/table/column 中的 NULL 表示
 * "该 plugin 不适用此层级",字符串 {@code "*"} 表示"该层级全部"。
 *
 * <p>编辑语义:管理员保存包内容 = smart diff(DELETE removed + INSERT added)。历史还原由
 * {@code t_r_data_perm_user_effective_snapshot}(用户级事实表)提供。
 *
 * <p>accesses 是 JSON 数组(plugin 原生 access 字符串列表),用 String 存,service 层用 JsonUtils 解析。
 */
@Data
@TableName("t_r_data_perm_role_permission")
public class DataPermRolePermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    /** 引用 {@code DataPermConfig.scopes[].code}。 */
    private Long scopeCode;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** JSON 数组字符串,如 {@code ["select","insert"]}。 */
    private String accesses;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
