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

package io.github.zzih.rudder.service.download;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流式下载写出器:内存峰值 = 单 batch + 底层 buffer,跟总行数无关。
 * 调用顺序固定:{@link #writeHeader} 一次 → {@link #writeRow} 多次 → {@link #close}。
 */
public interface ResultDownloadWriter extends Closeable {

    void writeHeader(List<String> columns) throws IOException;

    void writeRow(Map<String, Object> row) throws IOException;
}
