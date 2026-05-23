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

/**
 * 数据资源包定义。平台级,name 全局唯一,所属维度上不挂工作空间。
 *
 * <p>RBAC 语义:用户拿了 role 后跟随当前内容(改包自动同步);改包历史由
 * {@code t_r_data_perm_user_effective_snapshot}(用户级事实表)还原。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_data_perm_role")
public class DataPermRole extends BaseEntity {

    private String name;

    private String description;
}
