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

package io.github.zzih.rudder.task.api.task.sink;

import io.github.zzih.rudder.common.model.ColumnMeta;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流式结果写出口。{@link SqlExecutor} 边读 ResultSet 边喂行,实现侧自行决定脱敏 / 缓冲 / 落盘策略。
 * <p>
 * 通过这个接口反转依赖,SPI 层的 Task 不必感知 RedactionService / FileStorage / ResultFormat 等
 * 应用层 Bean,实现注入到 Task 后,数据"读 → 脱敏 → 落盘 → 上传"全部在 Task 内部走完,
 * Worker 只读出口元信息(resultPath / rowCount / firstRow)。
 *
 * <p>生命周期: {@link #init} → 多次 {@link #write} → {@link #close}。close 后调用 getter
 * 取出口元信息。
 */
public interface ResultSink {

    /**
     * 列元数据先行喂入,sink 用它决定脱敏匹配 / 写表头 / 列顺序。
     * 在第一条 {@link #write} 之前必须先调一次。
     */
    void init(List<ColumnMeta> columnMetas);

    /** 流式逐行写入。row 是列名 → 值的映射,值已经 toString 化。 */
    void write(Map<String, Object> row);

    /** 触发 flush + 上传。close 后才能保证 {@link #getResultPath} 返回值有效。 */
    void close() throws IOException;

    long getRowCount();

    /**
     * 上传后的 FileStorage 路径。In-memory 实现返回 null。
     * close 之前调用结果未定义。
     */
    String getResultPath();

    /**
     * 截留的首行,供工作流参数传递使用。空结果集返回 null。
     * sink 实现负责保留引用,不受后续 write 影响。
     */
    Map<String, Object> getFirstRow();

    List<ColumnMeta> getColumnMetas();
}
