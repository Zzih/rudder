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

package io.github.zzih.rudder.vector.qdrant;

import io.github.zzih.rudder.spi.api.model.HealthStatus;
import io.github.zzih.rudder.vector.api.VectorPoint;
import io.github.zzih.rudder.vector.api.VectorQuery;
import io.github.zzih.rudder.vector.api.VectorSearchHit;
import io.github.zzih.rudder.vector.api.VectorStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;

/**
 * Qdrant 实现,走 Spring AI starter 里传递进来的官方 {@link QdrantClient}(gRPC)。
 * Rudder 的多 collection 动态 SPI 模型下,我们直接用 {@link QdrantClient} 操作,
 * 而不用 Spring AI 的 {@code QdrantVectorStore}(该类绑单 collection + 强制 EmbeddingModel),
 * 保留 "按 collection 分区 + 向量来自上层 Embedding 流水线" 的既有语义。
 * <p>
 * 依赖来源:{@code spring-ai-starter-vector-store-qdrant} 传递拉入 {@code io.qdrant:client}。
 */
@Slf4j
public class QdrantVectorStore implements VectorStore, AutoCloseable {

    private final QdrantClient client;

    public QdrantVectorStore(String host, int port, boolean useTls, String apiKey) {
        QdrantGrpcClient.Builder b = QdrantGrpcClient.newBuilder(host, port, useTls);
        if (apiKey != null && !apiKey.isBlank()) {
            b.withApiKey(apiKey);
        }
        this.client = new QdrantClient(b.build());
    }

    // ==================== collection ====================

    @Override
    public void ensureCollection(String collection, int dimensions) {
        try {
            boolean exists = client.collectionExistsAsync(collection).get(10, TimeUnit.SECONDS);
            if (!exists) {
                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setSize(dimensions)
                        .setDistance(Collections.Distance.Cosine)
                        .build();
                client.createCollectionAsync(collection, params).get(30, TimeUnit.SECONDS);
                log.info("Qdrant collection created: {} dim={}", collection, dimensions);
            }
        } catch (Exception e) {
            log.error("ensureCollection failed for {}: {}", collection, e.getMessage());
            throw new RuntimeException("Qdrant ensureCollection failed: " + e.getMessage(), e);
        }
    }

    // ==================== upsert ====================

    @Override
    public void upsert(String collection, Collection<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<Points.PointStruct> structs = new ArrayList<>(points.size());
        for (VectorPoint p : points) {
            structs.add(toPointStruct(p));
        }
        try {
            client.upsertAsync(collection, structs).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Qdrant upsert failed collection={} size={}: {}", collection, points.size(), e.getMessage());
            throw new RuntimeException("Qdrant upsert failed: " + e.getMessage(), e);
        }
    }

    // ==================== delete ====================

    @Override
    public void deleteByIds(String collection, Collection<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        List<Common.PointId> ids = new ArrayList<>(pointIds.size());
        for (String id : pointIds) {
            ids.add(toPointId(id));
        }
        try {
            client.deleteAsync(collection, ids).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Qdrant delete by ids failed: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByPayload(String collection, String payloadKey, Object payloadValue) {
        Common.Filter filter = Common.Filter.newBuilder()
                .addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(payloadKey)
                                .setMatch(buildMatch(payloadValue))
                                .build())
                        .build())
                .build();
        try {
            client.deleteAsync(collection, filter).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Qdrant delete by payload failed: {}", e.getMessage());
        }
    }

    // ==================== search ====================

    @Override
    public List<VectorSearchHit> search(VectorQuery query) {
        if (query == null || query.getQueryVector() == null) {
            return List.of();
        }
        List<Float> vector = toFloatList(query.getQueryVector());
        Points.SearchPoints.Builder req = Points.SearchPoints.newBuilder()
                .setCollectionName(query.getCollection())
                .addAllVector(vector)
                .setLimit(query.getTopK() <= 0 ? 10 : query.getTopK())
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());
        if (query.getMinScore() != null) {
            req.setScoreThreshold(query.getMinScore().floatValue());
        }
        if (query.getPayloadFilter() != null && !query.getPayloadFilter().isEmpty()) {
            Common.Filter.Builder fb = Common.Filter.newBuilder();
            for (Map.Entry<String, Object> e : query.getPayloadFilter().entrySet()) {
                fb.addMust(Common.Condition.newBuilder()
                        .setField(Common.FieldCondition.newBuilder()
                                .setKey(e.getKey())
                                .setMatch(buildMatch(e.getValue()))
                                .build())
                        .build());
            }
            req.setFilter(fb.build());
        }
        try {
            List<Points.ScoredPoint> resp = client.searchAsync(req.build()).get(30, TimeUnit.SECONDS);
            List<VectorSearchHit> out = new ArrayList<>(resp.size());
            for (Points.ScoredPoint sp : resp) {
                Map<String, Object> payload = new HashMap<>();
                sp.getPayloadMap().forEach((k, v) -> payload.put(k, fromValue(v)));
                out.add(VectorSearchHit.builder()
                        .id(pointIdToString(sp.getId()))
                        .score(sp.getScore())
                        .payload(payload)
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("Qdrant search failed collection={}: {}", query.getCollection(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public HealthStatus healthCheck() {
        try {
            client.healthCheckAsync().get(5, TimeUnit.SECONDS);
            return HealthStatus.healthy();
        } catch (Exception e) {
            return HealthStatus.unhealthy(e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.debug("Qdrant client close error: {}", e.getMessage());
        }
    }

    // ==================== helpers ====================

    private Points.PointStruct toPointStruct(VectorPoint p) {
        Points.PointStruct.Builder b = Points.PointStruct.newBuilder()
                .setId(toPointId(p.getId()))
                .setVectors(Points.Vectors.newBuilder()
                        .setVector(Points.Vector.newBuilder().addAllData(toFloatList(p.getVector())).build())
                        .build());
        if (p.getPayload() != null) {
            p.getPayload().forEach((k, v) -> b.putPayload(k, toValue(v)));
        }
        return b.build();
    }

    private Common.PointId toPointId(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return Common.PointId.newBuilder().setUuid(uuid.toString()).build();
        } catch (IllegalArgumentException ignore) {
            try {
                long num = Long.parseLong(id);
                return Common.PointId.newBuilder().setNum(num).build();
            } catch (NumberFormatException e) {
                return Common.PointId.newBuilder().setUuid(id).build();
            }
        }
    }

    private String pointIdToString(Common.PointId id) {
        return id.hasUuid() ? id.getUuid() : Long.toString(id.getNum());
    }

    private Common.Match buildMatch(Object value) {
        Common.Match.Builder m = Common.Match.newBuilder();
        if (value instanceof Long l) {
            m.setInteger(l);
        } else if (value instanceof Integer i) {
            m.setInteger(i.longValue());
        } else if (value instanceof Boolean b) {
            m.setBoolean(b);
        } else {
            m.setKeyword(String.valueOf(value));
        }
        return m.build();
    }

    private Value toValue(Object o) {
        if (o == null) {
            return Value.newBuilder().setNullValueValue(0).build();
        }
        if (o instanceof String s) {
            return Value.newBuilder().setStringValue(s).build();
        }
        if (o instanceof Integer i) {
            return Value.newBuilder().setIntegerValue(i.longValue()).build();
        }
        if (o instanceof Long l) {
            return Value.newBuilder().setIntegerValue(l).build();
        }
        if (o instanceof Double d) {
            return Value.newBuilder().setDoubleValue(d).build();
        }
        if (o instanceof Float f) {
            return Value.newBuilder().setDoubleValue(f.doubleValue()).build();
        }
        if (o instanceof Boolean b) {
            return Value.newBuilder().setBoolValue(b).build();
        }
        return Value.newBuilder().setStringValue(String.valueOf(o)).build();
    }

    private Object fromValue(Value v) {
        return switch (v.getKindCase()) {
            case STRING_VALUE -> v.getStringValue();
            case INTEGER_VALUE -> v.getIntegerValue();
            case DOUBLE_VALUE -> v.getDoubleValue();
            case BOOL_VALUE -> v.getBoolValue();
            case NULL_VALUE -> null;
            default -> v.toString();
        };
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> out = new ArrayList<>(arr.length);
        for (float f : arr) {
            out.add(f);
        }
        return out;
    }
}
