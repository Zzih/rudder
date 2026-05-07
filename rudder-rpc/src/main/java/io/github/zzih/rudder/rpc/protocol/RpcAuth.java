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

package io.github.zzih.rudder.rpc.protocol;

import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * RPC 请求鉴权工具：基于 HMAC-SHA256 签名 + 时间戳防重放。
 * <p>
 * 签名内容：{@code methodId|opaque|timestamp}。
 * 服务端校验时拒绝：签名不匹配、时间戳超过允许偏移、签名/时间戳缺失。
 */
public final class RpcAuth {

    /** 允许的时间戳偏移（毫秒），超过即认为是重放请求 */
    public static final long MAX_SKEW_MS = 5 * 60 * 1000L;

    private RpcAuth() {
    }

    /**
     * 基于共享密钥生成请求签名。
     */
    public static String sign(String secret, String methodId, long opaque, long timestamp) {
        return CryptoUtils.hmacSha256Hex(secret, methodId + "|" + opaque + "|" + timestamp);
    }

    /**
     * 恒定时间比较签名。成功返回 true，否则返回 false。
     * 若时间戳偏移超过 {@link #MAX_SKEW_MS} 则视为失败。
     */
    public static boolean verify(String secret, String methodId, long opaque, long timestamp,
                                 String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        long skew = Math.abs(System.currentTimeMillis() - timestamp);
        if (skew > MAX_SKEW_MS) {
            return false;
        }
        String expected = sign(secret, methodId, opaque, timestamp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
