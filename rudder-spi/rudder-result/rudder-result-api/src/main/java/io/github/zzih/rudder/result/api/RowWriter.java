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

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * 流式行写出。{@link ResultFormat#openWriter} 打开,逐行 {@link #writeRow},{@link #close} 时
 * flush 到本地文件。调用方负责 close 后 upload 到 FileStorage。
 *
 * <p>不在内存里 buffer 全量行,内存峰值跟底层 writer 的 buffer(几百行)对齐。
 */
public interface RowWriter extends Closeable {

    void writeRow(Map<String, Object> row) throws IOException;
}
