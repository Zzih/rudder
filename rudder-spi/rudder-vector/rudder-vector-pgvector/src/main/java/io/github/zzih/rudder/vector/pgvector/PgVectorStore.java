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

package io.github.zzih.rudder.vector.pgvector;

import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.vector.api.VectorPoint;
import io.github.zzih.rudder.vector.api.VectorQuery;
import io.github.zzih.rudder.vector.api.VectorSearchHit;
import io.github.zzih.rudder.vector.api.VectorStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL pgvector 扩展实现。每个 Rudder collection 对应一张表,schema 动态建。
 * 走原生 JDBC + pgvector 内置 {@code <=>} 余弦距离运算符,不用 Spring AI 的 PgVectorVectorStore(其单 collection 绑定 + 强制 EmbeddingModel 与 Rudder SPI 不匹配)。
 * 依赖 {@code spring-ai-starter-vector-store-pgvector} 统一拉入 PG JDBC 驱动 + Spring AI 生态。
 */
@Slf4j
public class PgVectorStore implements VectorStore, AutoCloseable {

    private final DataSource dataSource;
    private final String schema;

    public PgVectorStore(PgVectorProperties p) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[]{p.getHost()});
        ds.setPortNumbers(new int[]{p.getPort()});
        ds.setDatabaseName(p.getDatabase());
        ds.setUser(p.getUsername());
        ds.setPassword(p.getPassword());
        this.dataSource = ds;
        this.schema = p.getSchema();
    }

    // ==================== collection (table) ====================

    @Override
    public void ensureCollection(String collection, int dimensions) {
        String table = qualifiedTable(collection);
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("CREATE EXTENSION IF NOT EXISTS vector")) {
                ps.execute();
            }
            try (
                    PreparedStatement ps = conn.prepareStatement(
                            "CREATE TABLE IF NOT EXISTS " + table + " ("
                                    + " id TEXT PRIMARY KEY,"
                                    + " payload JSONB,"
                                    + " embedding vector(" + dimensions + ")"
                                    + ")")) {
                ps.execute();
            }
            // HNSW index for ANN search
            try (
                    PreparedStatement ps = conn.prepareStatement(
                            "CREATE INDEX IF NOT EXISTS " + collection + "_embedding_hnsw ON " + table
                                    + " USING hnsw (embedding vector_cosine_ops)")) {
                ps.execute();
            }
            log.info("PgVector collection ensured: {} dim={}", table, dimensions);
        } catch (Exception e) {
            throw new RuntimeException("PgVector ensureCollection failed: " + e.getMessage(), e);
        }
    }

    // ==================== upsert ====================

    @Override
    public void upsert(String collection, Collection<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + qualifiedTable(collection)
                + " (id, payload, embedding) VALUES (?, ?::jsonb, ?::vector) "
                + "ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, embedding = EXCLUDED.embedding";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (VectorPoint p : points) {
                ps.setString(1, p.getId());
                ps.setString(2, p.getPayload() == null ? "{}" : JsonUtils.toJson(p.getPayload()));
                ps.setString(3, toVectorLiteral(p.getVector()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            log.error("PgVector upsert failed collection={} size={}: {}", collection, points.size(), e.getMessage());
            throw new RuntimeException("PgVector upsert failed: " + e.getMessage(), e);
        }
    }

    // ==================== delete ====================

    @Override
    public void deleteByIds(String collection, Collection<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", pointIds.stream().map(x -> "?").toList());
        String sql = "DELETE FROM " + qualifiedTable(collection) + " WHERE id IN (" + placeholders + ")";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String id : pointIds) {
                ps.setString(i++, id);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("PgVector delete by ids failed: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByPayload(String collection, String payloadKey, Object payloadValue) {
        String sql = "DELETE FROM " + qualifiedTable(collection) + " WHERE payload ->> ? = ?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payloadKey);
            ps.setString(2, String.valueOf(payloadValue));
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("PgVector delete by payload failed: {}", e.getMessage());
        }
    }

    // ==================== search ====================

    @Override
    public List<VectorSearchHit> search(VectorQuery query) {
        if (query == null || query.getQueryVector() == null) {
            return List.of();
        }
        int limit = query.getTopK() <= 0 ? 10 : query.getTopK();
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, payload, 1 - (embedding <=> ?::vector) AS score FROM ")
                .append(qualifiedTable(query.getCollection()));
        List<Object> args = new ArrayList<>();
        args.add(toVectorLiteral(query.getQueryVector()));
        if (query.getPayloadFilter() != null && !query.getPayloadFilter().isEmpty()) {
            List<String> conds = new ArrayList<>();
            for (Map.Entry<String, Object> e : query.getPayloadFilter().entrySet()) {
                conds.add("payload ->> ? = ?");
                args.add(e.getKey());
                args.add(String.valueOf(e.getValue()));
            }
            sql.append(" WHERE ").append(String.join(" AND ", conds));
        }
        if (query.getMinScore() != null) {
            sql.append(query.getPayloadFilter() != null && !query.getPayloadFilter().isEmpty() ? " AND " : " WHERE ")
                    .append("1 - (embedding <=> ?::vector) >= ?");
            args.add(toVectorLiteral(query.getQueryVector()));
            args.add(query.getMinScore());
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(toVectorLiteral(query.getQueryVector()));
        args.add(limit);

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            List<VectorSearchHit> out = new ArrayList<>(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> payload = new HashMap<>();
                    String payloadJson = rs.getString("payload");
                    if (payloadJson != null) {
                        try {
                            payload = JsonUtils.fromJson(payloadJson, new TypeReference<>() {
                            });
                        } catch (Exception e) {
                            log.debug("parse payload failed: {}", e.getMessage());
                        }
                    }
                    out.add(VectorSearchHit.builder()
                            .id(rs.getString("id"))
                            .score(rs.getFloat("score"))
                            .payload(payload)
                            .build());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("PgVector search failed collection={}: {}", query.getCollection(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public HealthStatus healthCheck() {
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ps.executeQuery();
            return HealthStatus.healthy();
        } catch (Exception e) {
            return HealthStatus.unhealthy(e.getMessage());
        }
    }

    @Override
    public void close() {
        // PGSimpleDataSource 无连接池,无需关闭
    }

    // ==================== helpers ====================

    private String qualifiedTable(String collection) {
        // 用 schema.collection,collection 名只允许 [a-z0-9_],避免注入
        if (!collection.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid collection name: " + collection);
        }
        return schema + ".\"" + collection + "\"";
    }

    private static String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }

}
