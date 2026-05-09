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

package io.github.zzih.rudder.dao.enums;

/**
 * 平台单 active SPI 配置类型鉴别器。{@code t_r_spi_config.type} 列。
 * 每个 type 内"单 active 选 provider"语义统一，由 type 隔离命名空间。
 *
 * <p>RAG_PIPELINE 不在此 enum——它不是 SPI 选型，是 RAG 链路参数配置，单独放
 * {@code t_r_rag_pipeline_config} 表。
 */
public enum SpiType {

    // 平台运行时基础设施
    FILE,
    RESULT,
    RUNTIME,
    METADATA,
    VERSION,

    // 业务流程
    APPROVAL,
    PUBLISH,
    NOTIFICATION,

    // AI
    LLM,
    EMBEDDING,
    VECTOR,
    RERANK
}
