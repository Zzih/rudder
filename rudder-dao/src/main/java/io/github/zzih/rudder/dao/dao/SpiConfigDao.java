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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.SpiConfig;
import io.github.zzih.rudder.dao.enums.SpiType;

import java.util.List;

public interface SpiConfigDao {

    /** 指定 type 下 enabled=1 的最新一条;未配置返回 null。 */
    SpiConfig selectActive(SpiType type);

    /** 按 (type, provider) 取唯一行(`uk_type_provider` 保证),不存在返回 null。 */
    SpiConfig selectByTypeAndProvider(SpiType type, String provider);

    /** 某 type 全部行(含 disabled),按 provider asc 排序。 */
    List<SpiConfig> selectAllByType(SpiType type);

    SpiConfig selectById(Long id);

    int insert(SpiConfig config);

    int updateById(SpiConfig config);

    /** 同 type 下、provider 不等于 keepProvider 的行 enabled 置 0,维持 per type 单 active。 */
    int disableOthers(SpiType type, String keepProvider);
}
