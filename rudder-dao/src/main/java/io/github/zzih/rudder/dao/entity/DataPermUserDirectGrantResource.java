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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** direct 授权块内一条库表行:每层 JSON 数组(多选)。语义同 {@link DataPermBundleStatementResource}。 */
@Data
@TableName("t_r_data_perm_user_direct_grant_resource")
public class DataPermUserDirectGrantResource implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long statementId;

    private String catalogNames;

    private String databaseNames;

    private String tableNames;

    private String columnNames;
}
