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

package io.github.zzih.rudder.spi.api.model;

/**
 * Provider 的元数据（版本 / 作者 / 描述 / 引入版本 / 文档链接），供管理页展示。
 *
 * <p>所有字段可空 — 没填就前端不展示。用 {@link #empty()} 当默认。
 */
public record ProviderMetadata(
        String version,
        String description,
        String author,
        String since,
        String docsUrl) {

    private static final ProviderMetadata EMPTY = new ProviderMetadata(null, null, null, null, null);

    public static ProviderMetadata empty() {
        return EMPTY;
    }

    /** 最小实例:新增 SPI 只需 `ProviderMetadata.of("一行说明")`。 */
    public static ProviderMetadata of(String description) {
        return new ProviderMetadata(null, description, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String version;
        private String description;
        private String author;
        private String since;
        private String docsUrl;

        public Builder version(String v) {
            this.version = v;
            return this;
        }
        public Builder description(String v) {
            this.description = v;
            return this;
        }
        public Builder author(String v) {
            this.author = v;
            return this;
        }
        public Builder since(String v) {
            this.since = v;
            return this;
        }
        public Builder docsUrl(String v) {
            this.docsUrl = v;
            return this;
        }

        public ProviderMetadata build() {
            return new ProviderMetadata(version, description, author, since, docsUrl);
        }
    }
}
