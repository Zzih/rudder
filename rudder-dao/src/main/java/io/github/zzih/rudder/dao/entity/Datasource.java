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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_datasource")
public class Datasource extends BaseEntity {

    private String name;

    @TableField("datasource_type")
    private String datasourceType;

    private String host;

    private Integer port;

    /**
     * JDBC URL 模板里 host/port 后面那一段,各引擎语义不同:
     * MySQL/PG → database,Hive → default schema,Trino → catalog。
     * <p>
     * ⚠️ 只用来构造 JDBC URL。**禁止**把它当业务身份使用(如
     * DataHub/元数据匹配),那些场景应该用运行时的 catalog / database 参
     * —— 一个 datasource 在连接层面可以跨库浏览。
     */
    @TableField("database_name")
    private String defaultPath;

    /**
     * 额外的连接参数，以 JSON 格式存储。
     */
    private String params;

    /**
     * 加密的凭证 JSON（用户名/密码）。
     */
    private String credential;
}
