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

package io.github.zzih.rudder.service.coordination;

/**
 * 跨节点广播事件契约。所有走 {@link RedisBroadcaster} 的事件类型必须实现本接口,
 * {@link RedisBroadcaster} 会按 {@link #originNodeId()} 自动过滤掉本节点自己发的消息
 * (避免自己广播自己收到导致回调被触发两次)。
 *
 * <p>典型实现是 record:
 * <pre>{@code
 * public record CacheEvent(String key, String subKey, boolean allKeys,
 *                          boolean prefixed, String originNodeId)
 *         implements BroadcastEvent {
 * }
 * }</pre>
 */
public interface BroadcastEvent {

    /** 事件来源节点 ID。{@link RedisBroadcaster} 用它做去重过滤。 */
    String originNodeId();
}
