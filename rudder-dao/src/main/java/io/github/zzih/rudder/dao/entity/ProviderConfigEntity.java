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

package io.github.zzih.rudder.dao.entity;

/**
 * 平台 SPI 配置实体的公共形态：{@code (provider, providerParams, enabled)}。
 * File / Result / Runtime / Metadata / Version / AiConfig 都满足此形态——
 * 由 Lombok 生成的 setter 直接满足接口契约，无需手写实现。
 *
 * <p>ApprovalConfig 用 {@code channel/channelParams} 命名不同，不实现本接口。
 */
public interface ProviderConfigEntity {

    void setProvider(String provider);

    void setProviderParams(String providerParams);

    void setEnabled(Boolean enabled);
}
