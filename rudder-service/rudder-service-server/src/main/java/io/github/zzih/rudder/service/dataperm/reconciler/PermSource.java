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

package io.github.zzih.rudder.service.dataperm.reconciler;

/**
 * 一条 effective perm 的来源指针。Reconciler 聚合时同步记录,写入
 * {@code t_r_data_perm_user_effective_snapshot.source_kinds}。
 *
 * @param kind 来源类型枚举,JSON 序列化为 {@code "ROLE"} / {@code "DIRECT"} 字面量
 * @param id   role_id 或 user_direct_grant.id
 */
public record PermSource(Kind kind, Long id) {

    public enum Kind {
        ROLE, DIRECT
    }

    public static PermSource role(Long roleId) {
        return new PermSource(Kind.ROLE, roleId);
    }

    public static PermSource direct(Long grantId) {
        return new PermSource(Kind.DIRECT, grantId);
    }

    /** 稳态 canonical 串,desired 与 snapshot 比对时共享。 */
    public String canonical() {
        return kind + ":" + id;
    }
}
