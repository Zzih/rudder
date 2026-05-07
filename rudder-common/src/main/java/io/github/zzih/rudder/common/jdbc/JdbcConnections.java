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

package io.github.zzih.rudder.common.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Function;

/**
 * 纯 JDBC 工具:开/关连接 + 模板执行。无任何业务概念(也不感知 SPI 的 DataSourceInfo),
 * 任何模块都可以用。调用方负责把数据源信息解构成 {@code (url, user, password, driverClass)} 传入。
 *
 * <p>SPI provider 用 {@link #runWith} 跑一次性 metadata 查询;长连接 / 连接池由调用方
 * 自己管(连接池实现属业务装配,不在 common 范畴)。
 */
public final class JdbcConnections {

    private JdbcConnections() {
    }

    /** 开一个新连接。调用方负责 close,推荐 try-with-resources。 */
    public static Connection open(String jdbcUrl, String username, String password,
                                  String driverClass) throws SQLException {
        ensureDriverLoaded(driverClass);
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * 开一个新连接,支持额外的 Properties(传给 driver 的 ssl / fetchsize / 自定义 hints)。
     * username / password 已并入 props,调用方在构造 props 时塞进去。
     */
    public static Connection open(String jdbcUrl, Properties props, String driverClass) throws SQLException {
        ensureDriverLoaded(driverClass);
        return DriverManager.getConnection(jdbcUrl, props);
    }

    private static void ensureDriverLoaded(String driverClass) {
        // JDBC 4 驱动会通过 META-INF/services 自注册;但少数老驱动(如 Hive)需要显式触发 SPI 注册。
        // Class.forName 自身命中 JVM ClassLoader 缓存,不需要再加一层手动 cache。
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("JDBC driver not on classpath: " + driverClass, e);
            }
        }
    }

    /** 模板:开连接 → 跑函数 → 自动关。SQL 异常包成 RuntimeException 让调用方一行写完。 */
    public static <T> T runWith(String jdbcUrl, String username, String password, String driverClass,
                                JdbcCallback<T> fn) {
        try (Connection conn = open(jdbcUrl, username, password, driverClass)) {
            return fn.apply(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC operation failed: " + e.getMessage(), e);
        }
    }

    /** 安全 close 任意 AutoCloseable,吞掉异常(场景:finally / 手动 close 链)。 */
    public static void safeClose(AutoCloseable... closeables) {
        if (closeables == null) {
            return;
        }
        for (AutoCloseable c : closeables) {
            if (c == null) {
                continue;
            }
            try {
                c.close();
            } catch (Exception ignored) {
                // 调用方走 safeClose 就是接受异常被吞
            }
        }
    }

    /** 同 {@link Function} 但允许抛 SQLException —— 让调用方写 conn → ResultSet 之类不必再包 try。 */
    @FunctionalInterface
    public interface JdbcCallback<T> {

        T apply(Connection conn) throws SQLException;
    }
}
