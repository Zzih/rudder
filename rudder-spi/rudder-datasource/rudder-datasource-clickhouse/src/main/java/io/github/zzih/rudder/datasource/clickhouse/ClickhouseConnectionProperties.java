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

package io.github.zzih.rudder.datasource.clickhouse;

/**
 * ClickHouse 连接参数。字段名是 ClickHouse JDBC driver 真实参数:{@code socket_timeout}
 * 走下划线(ClickHouse 协议层命名),{@code compress} / {@code ssl} 是布尔开关。
 */
@SuppressWarnings("checkstyle:MemberName") // socket_timeout 必须 snake_case 匹配 ClickHouse JDBC 协议
public record ClickhouseConnectionProperties(
        Integer socket_timeout,
        Boolean compress,
        Boolean ssl) {
}
