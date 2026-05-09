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

package io.github.zzih.rudder.runtime.aliyun;

/**
 * 阿里云 Runtime admin UI 提交的两段 properties 文本(config + envVars)。Provider
 * 实现内部 {@code parseProperties} 解析这两段文本,构造业务用的 {@link AliyunRuntimeProperties}。
 *
 * <p>之所以保留两段 properties 文本而非 flat 字段:Aliyun/AWS 配置项较多(spark/flink/region/key
 * /多个 workspace),flat 表单字段不直观;textarea + properties 让用户直接复制控制台导出的配置。
 */
public record AliyunRuntimeFormProperties(String config, String envVars) {

    public AliyunRuntimeFormProperties {
        if (config == null) {
            config = "";
        }
        if (envVars == null) {
            envVars = "";
        }
    }
}
