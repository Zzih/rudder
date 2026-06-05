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

package io.github.zzih.rudder.file.api;

import java.util.List;

/**
 * 目录列举的游标分页结果。统一为游标语义(不支持随机页 / total),但各 provider 的 cursor 编码不同:
 * 对象存储(OSS/S3)用原生不透明 continuationToken,稳定;文件系统(local/HDFS)用位置 offset(已消费条数),
 * 在两次取页之间目录被并发增删时可能跳过或重复边界条目。cursor 对调用方不透明,原样回传即可。
 *
 * @param entities   本页条目(展示排序由调用方负责)
 * @param nextCursor 下一页游标;{@code null} 表示已到末页
 */
public record StoragePage(List<StorageEntity> entities, String nextCursor) {

    public static StoragePage of(List<StorageEntity> entities, String nextCursor) {
        return new StoragePage(entities, nextCursor);
    }

    public static StoragePage empty() {
        return new StoragePage(List.of(), null);
    }
}
