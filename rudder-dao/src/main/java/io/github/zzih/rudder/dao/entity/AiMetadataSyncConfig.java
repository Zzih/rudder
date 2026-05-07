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

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_metadata_sync_config")
public class AiMetadataSyncConfig extends BaseEntity {

    private Long datasourceId;
    private Integer enabled;
    /** Spring cron,空=仅手动 */
    private String scheduleCron;
    /** JSON 数组:[catalog...](仅 3 层引擎,空=全部) */
    private String includeCatalogs;
    /** JSON 数组:[库名...](空=全部),3 层引擎元素含 catalog 前缀 "catalog.db" */
    private String includeDatabases;
    /** JSON 数组:[表名...](空=上层全部),元素含父级前缀 "db.table" 或 "catalog.db.table" */
    private String includeTables;
    /** JSON 数组:[关键字...] — 表名包含任一关键字即跳过,不区分大小写 substring */
    private String excludeTables;
    private Integer maxColumnsPerTable;
    /** JSON 数组:[{engine,template}] — 跨引擎访问路径 */
    private String accessPaths;
    private LocalDateTime lastSyncAt;
    /** SUCCESS|FAILED|RUNNING */
    private String lastSyncStatus;
    private String lastSyncMessage;
}
