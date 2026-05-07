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

package io.github.zzih.rudder.common.utils.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密/摘要工具类，集中 SHA-256、HMAC-SHA256、AES 等常用操作。
 */
public class CryptoUtils {

    private CryptoUtils() {
    }

    // ==================== 摘要 ====================

    /**
     * SHA-256 摘要，返回小写十六进制字符串。
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    /**
     * SHA-256 摘要，返回原始字节。
     */
    public static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    // ==================== HMAC ====================

    /**
     * HMAC-SHA256 签名，返回小写十六进制字符串。
     */
    public static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    // ==================== AES ====================

    /**
     * AES/ECB/PKCS5Padding 加密，返回 Base64 编码字符串。
     * 使用 SHA-256 从密钥文本派生 16 字节 AES 密钥。
     */
    public static String aesEncrypt(String plaintext, String keyText) {
        try {
            SecretKeySpec keySpec = deriveAesKey(keyText);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * AES/ECB/PKCS5Padding 解密，输入为 Base64 编码字符串。
     * 使用 SHA-256 从密钥文本派生 16 字节 AES 密钥。
     */
    public static String aesDecrypt(String encrypted, String keyText) {
        try {
            SecretKeySpec keySpec = deriveAesKey(keyText);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }

    private static SecretKeySpec deriveAesKey(String keyText) {
        byte[] hash = sha256(keyText);
        byte[] key = Arrays.copyOf(hash, 16);
        return new SecretKeySpec(key, "AES");
    }
}
