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

package io.github.zzih.rudder.datasource.service;

/**
 * 数据源配置变更(update / delete)事件。{@link ConnectionPoolManager} 订阅后 evict 对应池子,
 * 保证下一次 {@code getConnection} 用新凭证重建。
 * <p>
 * 事件而不是直接依赖,避免 DatasourceService ↔ ConnectionPoolManager 循环依赖。
 */
public record DatasourceChangedEvent(Long datasourceId) {
}
