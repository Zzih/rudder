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

import java.util.concurrent.atomic.AtomicLong;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RPC 消息头。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcHeader {

    private static final AtomicLong OPAQUE_SEQ = new AtomicLong(0);

    /** 方法标识符，格式：接口全限定名#方法名 */
    private String methodId;

    /** 请求 ID，用于匹配请求和响应 */
    private long opaque;

    /** 消息类型：0=请求，1=响应 */
    private int type;

    /** 请求时间戳（毫秒）。用于防重放校验 */
    private long timestamp;

    /** HMAC-SHA256 签名（hex）。{@link RpcAuth} 生成与校验 */
    private String signature;

    public static RpcHeader request(String methodId) {
        return new RpcHeader(methodId, OPAQUE_SEQ.incrementAndGet(), 0, 0L, null);
    }

    public static RpcHeader response(long opaque) {
        return new RpcHeader(null, opaque, 1, 0L, null);
    }

    public boolean isRequest() {
        return type == 0;
    }
}
