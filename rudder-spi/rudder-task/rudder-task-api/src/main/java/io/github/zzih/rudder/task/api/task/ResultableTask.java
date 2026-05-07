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

package io.github.zzih.rudder.task.api.task;

import io.github.zzih.rudder.common.model.ColumnMeta;
import io.github.zzih.rudder.task.api.task.sink.ResultSink;

import java.util.List;
import java.util.Map;

/**
 * 产生表格查询结果的 Task(SQL 查询 / SHOW 命令等)。
 * 数据出口走注入的 {@link ResultSink}:Task 内部边读边喂 {@code sink.write(row)},
 * sink 实现负责脱敏 / 落本地文件 / 上传 FileStorage。Task 只通过 sink 暴露轻量元信息
 * (resultPath / rowCount / firstRow / columnMetas)给 Worker。
 */
public interface ResultableTask extends Task {

    /** 资源注入阶段调用,Task 把 sink 传给 SqlExecutor.execute 喂数据。 */
    void setResultSink(ResultSink sink);

    List<ColumnMeta> getResultColumnMetas();

    long getRowCount();

    /** 上传后的 FileStorage 路径。In-memory sink 返回 null。 */
    String getResultPath();

    /** 截留的首行,供工作流参数传递。空结果集返回 null。 */
    Map<String, Object> getFirstRow();
}
