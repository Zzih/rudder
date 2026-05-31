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

package io.github.zzih.rudder.service.dataperm.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zzih.rudder.common.sql.TableAccess;

import org.junit.jupiter.api.Test;

class PluginTypeTest {

    /**
     * accessFor 与 baseAccessTypes 是两张手维护的同一套 plugin access 词表:本地鉴权用 accessFor 取 needAccess,
     * 写入/对账用 baseAccessTypes 作闭集校验。任何 accessFor 产出若不在 baseAccessTypes 内,就会出现
     * "本地放行但无法落成 policy" 或反向的静默不一致。锁死:每个 plugin 的非空 accessFor 结果必须 ∈ baseAccessTypes。
     */
    @Test
    void accessForOutputsAreSubsetOfBaseAccessTypes() {
        for (PluginType type : PluginType.values()) {
            if (type.baseAccessTypes().isEmpty()) {
                continue; // 占位 plugin,无 access 词表
            }
            for (TableAccess.Action action : TableAccess.Action.values()) {
                String access = type.accessFor(action);
                if (access != null) {
                    assertTrue(type.baseAccessTypes().contains(access),
                            () -> type + ".accessFor(" + action + ")=" + access + " must be in baseAccessTypes");
                }
            }
        }
    }
}
