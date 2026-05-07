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

package io.github.zzih.rudder.dao.config;

import io.github.zzih.rudder.common.context.UserContext;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

/**
 * MyBatis Plus auto-fill:为带 {@code @TableField(fill=...)} 的 createdBy/At/updatedBy/At
 * 字段自动注入审计信息。从 {@link UserContext} 拿当前用户 id,无 user 时填 0(系统操作)。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = UserContext.getUserId();
        long createdBy = userId != null ? userId : 0L;
        LocalDateTime now = LocalDateTime.now();

        strictInsertFill(metaObject, "createdBy", Long.class, createdBy);
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedBy", Long.class, createdBy);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = UserContext.getUserId();
        long updatedBy = userId != null ? userId : 0L;

        strictUpdateFill(metaObject, "updatedBy", Long.class, updatedBy);
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
