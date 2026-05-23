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

package io.github.zzih.rudder.datasource.trino;

/**
 * Trino 连接参数。字段名直接是 Trino JDBC driver 认识的 URL 参数名,大小写敏感
 * (官方文档 https://trino.io/docs/current/client/jdbc.html):{@code SSL} 大写,
 * {@code SSLTrustStorePath} / {@code SSLTrustStorePassword} 也大写 SSL 前缀。
 */
@SuppressWarnings("checkstyle:MemberName") // 字段名必须首字母大写匹配 Trino JDBC 协议
public record TrinoConnectionProperties(
        Boolean SSL,
        String SSLTrustStorePath,
        String SSLTrustStorePassword,
        String accessToken,
        String httpProxy) {
}
