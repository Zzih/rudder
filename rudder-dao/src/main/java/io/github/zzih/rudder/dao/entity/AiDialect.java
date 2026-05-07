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
 * t_r_ai_dialect —— AI 方言 prompt 的用户覆盖层。
 * <p>
 * 出厂默认存在 {@code classpath:ai-prompts/dialects/{TaskType}.md};admin 在 UI 里修改后
 * 会写入这张表,运行时优先使用 DB 版本,未命中时回退到 classpath。删除记录 = 回到出厂默认。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_r_ai_dialect")
public class AiDialect extends BaseEntity {

    /** TaskType 枚举名,如 {@code MYSQL} / {@code PYTHON} / {@code SEATUNNEL}。 */
    private String taskType;

    /** 覆盖的方言 prompt 正文。 */
    private String content;

    /** false = 忽略此覆盖,走 classpath 默认(留档的同时暂时关闭)。 */
    private Boolean enabled;
}
