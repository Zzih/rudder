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

package io.github.zzih.rudder.common.enums.dataperm;

/** 用户 grant 失效原因,落入 {@code t_r_data_perm_user_{role,direct}_grant.end_reason}。 */
public enum DataPermGrantEndReason {
    /** 自然到期(到达 expiration_time)。 */
    EXPIRED,
    /** 管理员或申请人主动撤销。 */
    REVOKED,
    /** 关联资源包被删除导致 role grant 级联失效。 */
    ROLE_DELETED
}
