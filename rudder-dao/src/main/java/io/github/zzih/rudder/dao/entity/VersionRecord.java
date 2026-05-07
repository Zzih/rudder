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

import io.github.zzih.rudder.common.enums.datatype.ResourceType;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_r_version_record")
public class VersionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private ResourceType resourceType;
    private Long resourceCode;
    private Integer versionNo;
    /** 由 SPI provider 自描述的存储引用:LOCAL 直接是内容,GIT 是 GitRef JSON。 */
    private String storageRef;
    /** 写入这条版本时使用的 provider(LOCAL / GIT)。load 时按它路由到对应 SPI。 */
    private String provider;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
}
