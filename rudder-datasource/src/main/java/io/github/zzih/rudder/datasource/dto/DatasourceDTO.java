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

package io.github.zzih.rudder.datasource.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Data;

@Data
public class DatasourceDTO {

    @JsonPropertyDescription("Internal id (server-assigned, ignored on create)")
    private Long id;

    @JsonPropertyDescription("Datasource display name (globally unique)")
    private String name;

    @JsonPropertyDescription("Engine type: MYSQL / HIVE / SPARK / STARROCKS / TRINO / FLINK")
    private String datasourceType;

    @JsonPropertyDescription("Host or IP of the database server")
    private String host;

    @JsonPropertyDescription("Service port (e.g. 3306 for MySQL, 10000 for Hive)")
    private Integer port;

    /** JDBC URL 里的路径段:MySQL/PG→database, Hive→schema, Trino→catalog。仅用于构造 URL。 */
    @JsonPropertyDescription("Path segment of the JDBC URL: MySQL/PG → database, Hive → schema, Trino → catalog")
    private String defaultPath;

    @JsonPropertyDescription("Extra connection params as JSON string (e.g. '{\"useSSL\":\"false\"}')")
    private String params;

    @JsonPropertyDescription("Creation timestamp (server-managed)")
    private LocalDateTime createdAt;

    @JsonPropertyDescription("Last update timestamp (server-managed)")
    private LocalDateTime updatedAt;
}
