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

package io.github.zzih.rudder.service.dataperm.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;

import java.util.List;

import org.junit.jupiter.api.Test;

class DataPermStatementSupportTest {

    private static final List<ResourceLevel> FULL = List.of(
            ResourceLevel.CATALOG, ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN);

    /** 直连 API 绕过编辑器的退化输入:适用层全空只在最深层给具体值,reconciler 会把上层按 "*" 物化。 */
    @Test
    void canonicalize_emptyApplicableLevelsBecomeStar() {
        List<List<String>> out = DataPermStatementSupport.canonicalizeForcedAll(
                FULL, List.of(), List.of(), List.of(), List.of("c1"));
        assertThat(out).containsExactly(
                List.of("*"), List.of("*"), List.of("*"), List.of("c1"));
    }

    /** 不适用层(plugin 无该层级)留空不动,不得被补成 "*"。 */
    @Test
    void canonicalize_inapplicableLevelsStayEmpty() {
        List<List<String>> out = DataPermStatementSupport.canonicalizeForcedAll(
                List.of(ResourceLevel.DATABASE, ResourceLevel.TABLE),
                List.of(), List.of(), List.of(), List.of());
        assertThat(out.get(0)).isEmpty();
        assertThat(out.get(1)).containsExactly("*");
        assertThat(out.get(2)).containsExactly("*");
        assertThat(out.get(3)).isEmpty();
    }

    /** 具体值与显式 "*" 原样保留;仅尾部空适用层补 "*"。 */
    @Test
    void canonicalize_concreteAndExplicitStarPreserved() {
        List<List<String>> out = DataPermStatementSupport.canonicalizeForcedAll(
                FULL, List.of("c1"), List.of("d1"), List.of("t1"), List.of());
        assertThat(out).containsExactly(
                List.of("c1"), List.of("d1"), List.of("t1"), List.of("*"));
    }

    /** SCHEMA 等价 DATABASE 层:Trino 的 schema 映射到 database 字段。 */
    @Test
    void canonicalize_schemaCountsAsDatabaseLevel() {
        List<List<String>> out = DataPermStatementSupport.canonicalizeForcedAll(
                List.of(ResourceLevel.CATALOG, ResourceLevel.SCHEMA, ResourceLevel.TABLE),
                List.of("c1"), List.of(), List.of("t1"), List.of());
        assertThat(out.get(1)).containsExactly("*");
        assertThat(out.get(3)).isEmpty();
    }
}
