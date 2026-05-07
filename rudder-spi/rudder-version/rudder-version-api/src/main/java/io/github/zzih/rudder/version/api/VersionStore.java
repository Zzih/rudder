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

package io.github.zzih.rudder.version.api;

import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.version.api.model.VersionRecord;

import java.util.Map;

/**
 * 版本内容存储后端契约。SPI 只关心"内容怎么进存储后端、怎么从后端拿回",
 * 不接触版本号生成、列表、分页、归属校验等平台通用语义(由宿主 {@code VersionService} 完成)。
 *
 * <p>{@code storageRef} 是 provider 自描述的字符串:LOCAL 直接是内容本身;GIT 是 JSON
 * {@code {sha,path,org,repo}}。VersionService 完全不解释,原样写入和读出
 * {@code t_r_version_record.storage_ref} 列。
 */
public interface VersionStore extends AutoCloseable {

    /** 健康检查。默认 UNKNOWN(provider 未实现)。 */
    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

    @Override
    default void close() {
    }

    /**
     * 把单文件内容存到后端。
     *
     * @param record 含 content + attributes(LOCAL 忽略,GIT 用来拼 path/org/repo)
     * @return storageRef(provider 自描述)
     */
    String save(VersionRecord record);

    /**
     * 多文件保存(工作流场景:dag.json + tasks/ + scripts/ 一次提交)。默认走 {@link #save(VersionRecord)}。
     */
    default String saveMultiFile(VersionRecord record, Map<String, String> files) {
        return save(record);
    }

    /**
     * 按 storageRef 还原内容。
     */
    String load(String storageRef);
}
