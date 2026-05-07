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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RPC 响应体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcResponse {

    private boolean success;

    /** 错误信息（失败时） */
    private String error;

    /** 序列化后的返回值 JSON 字节 */
    private byte[] body;

    /** 返回值类型全限定名 */
    private String bodyType;

    public static RpcResponse ok(byte[] body, String bodyType) {
        return new RpcResponse(true, null, body, bodyType);
    }

    public static RpcResponse ok() {
        return new RpcResponse(true, null, null, null);
    }

    public static RpcResponse fail(String error) {
        return new RpcResponse(false, error, null, null);
    }
}
