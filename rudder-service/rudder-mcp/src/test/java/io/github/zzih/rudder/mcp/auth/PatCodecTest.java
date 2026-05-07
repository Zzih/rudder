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

package io.github.zzih.rudder.mcp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatCodecTest {

    @Test
    @DisplayName("generate: 前缀 rdr_pat_ + 高熵随机后缀")
    void generateProducesValidToken() {
        String token = PatCodec.generate();
        assertThat(token).startsWith("rdr_pat_");
        assertThat(token.length()).isGreaterThan(PatCodec.PREFIX_LENGTH);
        // 32 字节 base64-url no-padding ≈ 43 字符
        assertThat(token.length() - PatCodec.TOKEN_HEADER.length()).isBetween(40, 50);
    }

    @Test
    @DisplayName("generate: 多次调用不重复（熵足够）")
    void generateProducesDistinctTokens() {
        String t1 = PatCodec.generate();
        String t2 = PatCodec.generate();
        String t3 = PatCodec.generate();
        assertThat(t1).isNotEqualTo(t2);
        assertThat(t2).isNotEqualTo(t3);
        assertThat(t1).isNotEqualTo(t3);
    }

    @Test
    @DisplayName("prefixOf: 取前 12 字符（rdr_pat_ + 4 字符随机）")
    void prefixOfTakesFirst12() {
        String token = PatCodec.generate();
        String prefix = PatCodec.prefixOf(token);
        assertThat(prefix).hasSize(PatCodec.PREFIX_LENGTH);
        assertThat(prefix).startsWith("rdr_pat_");
        assertThat(token).startsWith(prefix);
    }

    @Test
    @DisplayName("prefixOf: 错误格式抛 IllegalArgumentException")
    void prefixOfRejectsMalformed() {
        assertThatThrownBy(() -> PatCodec.prefixOf("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatCodec.prefixOf(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatCodec.prefixOf("rdr_pat_"))
                .as("仅前缀无随机熵也应拒绝")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isWellFormed: 边界用例")
    void isWellFormedEdgeCases() {
        assertThat(PatCodec.isWellFormed(null)).isFalse();
        assertThat(PatCodec.isWellFormed("")).isFalse();
        assertThat(PatCodec.isWellFormed("rdr_pat_")).as("仅前缀无随机熵").isFalse();
        assertThat(PatCodec.isWellFormed("rdr_pat_xxxx")).as("12 字符等阈值,需严格大于").isFalse();
        assertThat(PatCodec.isWellFormed("rdr_pat_xxxxx")).as("13 字符达到最小有效长度").isTrue();
        assertThat(PatCodec.isWellFormed("xyz_abc_xxxxxxxx")).as("前缀错误").isFalse();
    }
}
