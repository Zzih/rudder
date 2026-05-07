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

package io.github.zzih.rudder.ai.orchestrator;

import java.security.SecureRandom;

/**
 * 26-char Crockford Base32 ULID 生成器(参考 spec:https://github.com/ulid/spec)。
 * 前 10 字符编码时间戳,后 16 字符随机,可字典序排序。
 */
public final class Ulid {

    private static final char[] ENCODE = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String newUlid() {
        long ts = System.currentTimeMillis();
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        char[] out = new char[26];
        // 时间戳 48 bit → 10 个 base32 字符
        for (int i = 9; i >= 0; i--) {
            out[i] = ENCODE[(int) (ts & 0x1F)];
            ts >>>= 5;
        }
        // 随机 80 bit → 16 个 base32 字符
        // 把 10 字节看成 80 bit 流,每 5 bit 一字符
        long hi = 0;
        long lo = 0;
        for (int i = 0; i < 5; i++) {
            hi = (hi << 8) | (rand[i] & 0xFF);
        }
        for (int i = 5; i < 10; i++) {
            lo = (lo << 8) | (rand[i] & 0xFF);
        }
        for (int i = 15; i >= 8; i--) {
            out[10 + i] = ENCODE[(int) (lo & 0x1F)];
            lo >>>= 5;
        }
        for (int i = 7; i >= 0; i--) {
            out[10 + i] = ENCODE[(int) (hi & 0x1F)];
            hi >>>= 5;
        }
        return new String(out);
    }
}
