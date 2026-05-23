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

package io.github.zzih.rudder.service.dataperm.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.client.ranger.RangerAdminRestClient;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.config.PluginType;

import java.util.List;

import org.junit.jupiter.api.Test;

class RangerAdapterRegistryTest {

    /** Registry 测试只看 supportedPluginType(),adapter 内部 cache / config 不触发,null 注入即可。 */
    private static final GlobalCacheService NULL_CACHE = null;
    private static final RangerAdminRestClient NULL_CLIENT = null;
    private static final DataPermConfigService NULL_CONFIG = null;

    private RangerAdapterRegistry buildRegistry() {
        return new RangerAdapterRegistry(List.of(
                new HadoopSqlRangerAdapter(NULL_CACHE, NULL_CLIENT, NULL_CONFIG),
                new TrinoRangerResourceAdapter(NULL_CACHE, NULL_CLIENT, NULL_CONFIG),
                new StarRocksRangerResourceAdapter(NULL_CACHE, NULL_CLIENT, NULL_CONFIG)));
    }

    @Test
    void registry_contains_all_registered_types() {
        RangerAdapterRegistry registry = buildRegistry();
        assertThat(registry.registeredTypes())
                .containsExactlyInAnyOrder(PluginType.HADOOP_SQL, PluginType.TRINO, PluginType.STARROCKS);
    }

    @Test
    void find_known_returnsAdapter() {
        RangerAdapterRegistry registry = buildRegistry();
        assertThat(registry.find(PluginType.HADOOP_SQL)).isPresent();
        assertThat(registry.find(PluginType.TRINO)).isPresent();
        assertThat(registry.find(PluginType.STARROCKS)).isPresent();
    }

    @Test
    void find_unregistered_returnsEmpty() {
        RangerAdapterRegistry registry = buildRegistry();
        assertThat(registry.find(PluginType.HBASE)).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void require_unregistered_throwsRangerServiceNotFound() {
        RangerAdapterRegistry registry = buildRegistry();
        assertThatThrownBy(() -> registry.require(PluginType.HBASE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND);
    }

    @Test
    void duplicate_pluginTypeFailsAtConstruction() {
        assertThatThrownBy(() -> new RangerAdapterRegistry(List.of(
                new HadoopSqlRangerAdapter(NULL_CACHE, NULL_CLIENT, NULL_CONFIG),
                new HadoopSqlRangerAdapter(NULL_CACHE, NULL_CLIENT, NULL_CONFIG))))
                .isInstanceOf(IllegalStateException.class);
    }
}
