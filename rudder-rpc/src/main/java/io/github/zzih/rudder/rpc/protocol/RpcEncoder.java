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

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC 消息编码器。
 * <pre>
 * 格式：[MAGIC:2] [VERSION:1] [HEADER_LEN:4] [BODY_LEN:4] [HEADER] [BODY]
 * </pre>
 */
public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        byte[] headerBytes = JsonUtils.toJson(msg.getHeader()).getBytes();
        byte[] bodyBytes = msg.getBody() != null ? msg.getBody() : new byte[0];

        out.writeShort(RpcMessage.MAGIC);
        out.writeByte(RpcMessage.VERSION);
        out.writeInt(headerBytes.length);
        out.writeInt(bodyBytes.length);
        out.writeBytes(headerBytes);
        out.writeBytes(bodyBytes);
    }
}
