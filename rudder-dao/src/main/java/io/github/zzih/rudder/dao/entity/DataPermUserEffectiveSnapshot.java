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
 * 用户实际权限快照(Ranger 端事实表)。
 *
 * <p>Reconciler apply 成功后,跟最近 version 的快照全量 diff,有差异才一次性写一份新 version(N 条 perm 共享同一 version
 * 与 snapshot_time);apply 失败 / 无差异 / 用户无权限 一律不写。
 *
 * <p>查最新:{@code MAX(version) WHERE user_id=?};查 T 时刻:{@code MAX(version) WHERE snapshot_time<=T}。
 */
@Data
@TableName("t_r_data_perm_user_effective_snapshot")
public class DataPermUserEffectiveSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long version;

    private LocalDateTime snapshotTime;

    private Long scopeCode;

    private String catalogName;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** JSON 数组字符串,如 {@code ["select","insert"]}。 */
    private String accesses;

    /** JSON 数组字符串,如 {@code [{"kind":"ROLE","id":7},{"kind":"DIRECT","id":42}]}。 */
    private String sourceKinds;
}
