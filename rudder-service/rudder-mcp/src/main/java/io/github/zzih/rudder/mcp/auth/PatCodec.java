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

import java.security.SecureRandom;
import java.util.Base64;

/**
 * MCP Personal Access Token 编码 / 解码。
 *
 * <p>格式：{@code rdr_pat_<base64url-32-byte>}
 * <ul>
 *   <li>固定前缀 {@code rdr_pat_}（8 字符）— 便于 git pre-commit hook 扫描泄漏</li>
 *   <li>32 字节随机熵（256 bit），URL-safe base64 编码后约 43 字符</li>
 *   <li>{@link #prefixOf(String)} 取前 12 字符（{@code rdr_pat_xxxx}）作为 DB 索引主查询路径</li>
 *   <li>{@link #verify(String, String)} 用 bcrypt 比对完整 token 与 DB hash</li>
 * </ul>
 */
public final class PatCodec {

    public static final String TOKEN_HEADER = "rdr_pat_";

    /** DB token_prefix 字段长度：包含 "rdr_pat_" 8 字符 + 4 字符随机熵作为前缀，共 12 字符。 */
    public static final int PREFIX_LENGTH = 12;

    private static final int RANDOM_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PatCodec() {
    }

    /** 生成新 token 明文。仅在创建时调用一次。 */
    public static String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return TOKEN_HEADER + body;
    }

    /** 取 DB 索引前缀。token 必须以 {@link #TOKEN_HEADER} 开头且足够长。 */
    public static String prefixOf(String token) {
        if (!isWellFormed(token)) {
            throw new IllegalArgumentException("Malformed MCP token");
        }
        return token.substring(0, PREFIX_LENGTH);
    }

    public static boolean isWellFormed(String token) {
        return token != null
                && token.startsWith(TOKEN_HEADER)
                && token.length() > PREFIX_LENGTH;
    }
}
