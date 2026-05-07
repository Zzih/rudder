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

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 消息解码器。
 * <pre>
 * 格式：[MAGIC:2] [VERSION:1] [HEADER_LEN:4] [BODY_LEN:4] [HEADER] [BODY]
 * 固定头部：11 字节
 * </pre>
 */
@Slf4j
public class RpcDecoder extends ByteToMessageDecoder {

    private static final int HEADER_SIZE = 2 + 1 + 4 + 4; // magic + version + headerLen + bodyLen

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < HEADER_SIZE) {
            return;
        }

        in.markReaderIndex();

        short magic = in.readShort();
        if (magic != RpcMessage.MAGIC) {
            log.warn("Invalid magic: 0x{}, closing channel", Integer.toHexString(magic & 0xFFFF));
            ctx.close();
            return;
        }

        in.readByte(); // version
        int headerLen = in.readInt();
        int bodyLen = in.readInt();

        if (in.readableBytes() < headerLen + bodyLen) {
            in.resetReaderIndex();
            return;
        }

        byte[] headerBytes = new byte[headerLen];
        in.readBytes(headerBytes);
        RpcHeader header = JsonUtils.fromJson(new String(headerBytes), RpcHeader.class);

        byte[] bodyBytes = new byte[bodyLen];
        in.readBytes(bodyBytes);

        out.add(new RpcMessage(header, bodyBytes));
    }
}
