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

package io.github.zzih.rudder.task.api.log;

import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;

/**
 * Logback 区分器，从 MDC 中返回任务日志文件路径。
 * SiftingAppender 使用此区分器为每个任务实例创建独立的 FileAppender。
 */
public class TaskLogDiscriminator extends AbstractDiscriminator<ILoggingEvent> {

    private static final String DEFAULT_VALUE = "unknown";

    private String key = TaskLogUtils.MDC_TASK_LOG_PATH;

    @Override
    public String getDiscriminatingValue(ILoggingEvent event) {
        String val = MDC.get(key);
        return (val != null && !val.isEmpty()) ? val : DEFAULT_VALUE;
    }

    @Override
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
