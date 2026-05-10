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

package io.github.zzih.rudder.publish.api.bundle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源(JAR / 配置文件 等)。Bundle 中直接携带字节,接收侧自行决定怎么落盘 / 同步到 DS。
 *
 * <p>Jackson 默认以 Base64 字符串序列化 {@code byte[]},适用于中小文件。
 * 大文件场景的 dedup / 流式优化暂不实现,后续再视实际负载调整。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceBundle {

    /** Rudder FileStorage 中的相对路径(如 {@code "demo-jars/spark/foo.jar"})。 */
    private String path;

    /** 文件名(如 {@code "foo.jar"}),通常由 path 末段提取。接收侧可据此命名 DS 资源中心的条目。 */
    private String name;

    /** 字节数。 */
    private Long size;

    /** 内容 SHA-256 hex(64 字符)。接收侧拿到 {@code content} 后应再次计算并比对,防网络损坏静默落盘。 */
    private String sha256;

    /** 文件字节内容。Jackson 序列化为 Base64 字符串。 */
    private byte[] content;
}
