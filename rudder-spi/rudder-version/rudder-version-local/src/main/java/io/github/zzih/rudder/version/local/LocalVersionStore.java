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

package io.github.zzih.rudder.version.local;

import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.model.VersionRecord;

/**
 * LOCAL provider:版本内容直接写入 {@code t_r_version_record.storage_ref} 列。
 * <p>
 * "存储后端"就是宿主的同一张表,所以 storageRef = 内容本身,save/load 都是无脑透传。
 * 版本号生成、列表分页等由宿主 {@code VersionService} 完成。
 */
public class LocalVersionStore implements VersionStore {

    @Override
    public String save(VersionRecord record) {
        return record.getContent();
    }

    @Override
    public String load(String storageRef) {
        return storageRef;
    }
}
