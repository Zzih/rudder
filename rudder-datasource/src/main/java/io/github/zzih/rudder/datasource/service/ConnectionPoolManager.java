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

package io.github.zzih.rudder.datasource.service;

import io.github.zzih.rudder.common.enums.error.DatasourceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.spi.api.context.DataSourceInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 为元数据查询提供 JDBC 连接,**每个数据源一个 HikariCP 连接池**。
 * <p>
 * 从原先的 {@code DriverManager.getConnection()} 每次新建连接改造:
 * metadata tree 展开 / listColumns / listTables 等高频路径不再反复握手,单次请求延迟从数百 ms 降至 ms 级。
 * <p>
 * 数据源配置变更时,调用方应调 {@link #evict(Long)} 让池子重建(避免使用旧凭证/旧 URL)。
 */
@Slf4j
@Service
public class ConnectionPoolManager {

    /** 每个数据源的连接池大小。元数据查询并发不高,小池子即可。 */
    private static final int POOL_MAX_SIZE = 5;
    private static final int POOL_MIN_IDLE = 1;
    private static final long POOL_IDLE_TIMEOUT_MS = 5 * 60_000L;
    private static final long POOL_CONNECTION_TIMEOUT_MS = 10_000L;

    private final DatasourceService datasourceService;
    private final ConcurrentMap<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public ConnectionPoolManager(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    public Connection getConnection(Long datasourceId) {
        HikariDataSource pool = pools.computeIfAbsent(datasourceId, this::createPool);
        try {
            return pool.getConnection();
        } catch (SQLException e) {
            throw new BizException(DatasourceErrorCode.DS_POOL_ERROR,
                    "Failed to borrow connection for datasource " + datasourceId + ": " + e.getMessage());
        }
    }

    /** 数据源配置变更 / 删除后,evict 对应池子让下次 getConnection 重建。 */
    public void evict(Long datasourceId) {
        HikariDataSource pool = pools.remove(datasourceId);
        if (pool != null) {
            closeQuietly(pool, datasourceId);
        }
    }

    @EventListener
    public void onDatasourceChanged(DatasourceChangedEvent event) {
        evict(event.datasourceId());
    }

    private HikariDataSource createPool(Long datasourceId) {
        DataSourceInfo info = datasourceService.getDataSourceInfo(datasourceId);
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(info.getJdbcUrl());
        if (info.getUsername() != null) {
            cfg.setUsername(info.getUsername());
        }
        if (info.getPassword() != null) {
            cfg.setPassword(info.getPassword());
        }
        if (info.getProperties() != null) {
            info.getProperties().forEach((k, v) -> cfg.addDataSourceProperty(Objects.toString(k), v));
        }
        cfg.setPoolName("rudder-ds-" + datasourceId);
        cfg.setMaximumPoolSize(POOL_MAX_SIZE);
        cfg.setMinimumIdle(POOL_MIN_IDLE);
        cfg.setIdleTimeout(POOL_IDLE_TIMEOUT_MS);
        cfg.setConnectionTimeout(POOL_CONNECTION_TIMEOUT_MS);
        // 元数据库多为远端(Hive/Trino),连接建立慢,让 idle 多保留一点
        cfg.setMaxLifetime(30 * 60_000L);
        log.info("Init metadata pool for datasource {}: url={}, maxSize={}", datasourceId, info.getJdbcUrl(),
                POOL_MAX_SIZE);
        return new HikariDataSource(cfg);
    }

    @PreDestroy
    public void closeAll() {
        pools.forEach((id, pool) -> closeQuietly(pool, id));
        pools.clear();
    }

    private static void closeQuietly(HikariDataSource pool, Long datasourceId) {
        try {
            pool.close();
        } catch (Exception e) {
            log.warn("Failed to close metadata pool for datasource {}: {}", datasourceId, e.getMessage());
        }
    }
}
