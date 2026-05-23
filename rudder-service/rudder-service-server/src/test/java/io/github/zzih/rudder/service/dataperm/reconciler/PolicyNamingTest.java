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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.service.dataperm.config.ResourceLevel;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolicyNamingTest {

    @Test
    void build_hivePath() {
        String name = PolicyNaming.build(
                List.of(ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN),
                List.of("ods", "orders", "email"));
        assertThat(name).isEqualTo("rudder-ods/orders/email");
    }

    @Test
    void build_starReplacedToAll() {
        String name = PolicyNaming.build(
                List.of(ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN),
                List.of("ods", "orders", "*"));
        assertThat(name).isEqualTo("rudder-ods/orders/_ALL_");
    }

    @Test
    void build_nullReplacedToAll() {
        String name = PolicyNaming.build(
                List.of(ResourceLevel.DATABASE, ResourceLevel.TABLE, ResourceLevel.COLUMN),
                java.util.Arrays.asList("ods", null, ""));
        assertThat(name).isEqualTo("rudder-ods/_ALL_/_ALL_");
    }

    @Test
    void build_trinoPathFourLevels() {
        String name = PolicyNaming.build(
                List.of(ResourceLevel.CATALOG, ResourceLevel.SCHEMA,
                        ResourceLevel.TABLE, ResourceLevel.COLUMN),
                List.of("wh", "pub", "t1", "*"));
        assertThat(name).isEqualTo("rudder-wh/pub/t1/_ALL_");
    }

    @Test
    void build_starrocks_fourLevels() {
        String name = PolicyNaming.build(
                List.of(ResourceLevel.CATALOG, ResourceLevel.DATABASE,
                        ResourceLevel.TABLE, ResourceLevel.COLUMN),
                List.of("default", "mart", "*", "*"));
        assertThat(name).isEqualTo("rudder-default/mart/_ALL_/_ALL_");
    }

    @Test
    void build_sizeMismatch_throws() {
        assertThatThrownBy(() -> PolicyNaming.build(
                List.of(ResourceLevel.DATABASE), List.of("a", "b")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isRudderPolicy() {
        assertThat(PolicyNaming.isRudderPolicy("rudder-a/b/c")).isTrue();
        assertThat(PolicyNaming.isRudderPolicy("some-other-policy")).isFalse();
        assertThat(PolicyNaming.isRudderPolicy(null)).isFalse();
    }
}
