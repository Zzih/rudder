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

package io.github.zzih.rudder.result.api;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.spi.api.model.HealthStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 任务结果存储格式 SPI。
 * <p>
 * 通过 {@link FileStorage} 读写，支持本地 / HDFS / S3 / OSS 等存储后端。
 * 由 {@code ResultPluginManager} 通过 {@link io.github.zzih.rudder.result.api.spi.ResultFormatFactory}
 * 注册并按 {@code t_r_spi_config} 中 type=RESULT 行切换激活格式。
 *
 * <p>写出走流式 {@link #openWriter}：逐行 writeRow → close → upload，内存峰值不受总行数影响。
 */
public interface ResultFormat {

    /** 格式名称，如 "json", "csv", "parquet" */
    String name();

    /** 文件扩展名，如 ".jsonl", ".csv", ".parquet" */
    String extension();

    /**
     * format 声明的伴生文件后缀列表(相对数据文件)。
     * <p>例如 CSV/JSON 用 sidecar 索引就返回 {@code [".idx"]};Parquet/ORC 元数据自带在文件
     * footer 里,无伴生,返回 {@code []}。
     * <p>上层 (Sink upload / Service 预下载) 按这个列表遍历搬运,**绝不**硬编码后缀名 ——
     * 这样 format 内部用什么物理索引机制对上层完全透明。
     */
    default List<String> sidecarSuffixes() {
        return List.of();
    }

    /**
     * 在本地临时文件上打开流式 writer。调用方负责 close 后把文件 upload 到 FileStorage。
     * Excel/Parquet/ORC/Avro 等需要 random-access 或 footer 的格式必须先写本地。
     */
    RowWriter openWriter(Path localFile, List<String> columns) throws IOException;

    /** 从 FileStorage 分页读取结果 */
    ResultPage read(FileStorage storage, String path, int offset, int limit) throws IOException;

    /** 获取总行数 */
    long rowCount(FileStorage storage, String path) throws IOException;
    /** 健康检查。SPI 默认返回 unknown()；provider 视情况覆盖。 */
    default HealthStatus healthCheck() {
        return HealthStatus.unknown();
    }

}
