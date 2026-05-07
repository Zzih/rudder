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

package io.github.zzih.rudder.service.stream;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * 单个流(turn)的取消句柄。
 * 由 StreamRegistry 创建并托管,provider 循环、token flusher、tool 调用都需要持有这个引用
 * 并在循环间检查 {@link #isCancelled()}。
 * <p>
 * 支持三种取消途径,cancel 会依次尝试:
 * <ol>
 *   <li>设置 {@link #cancelled} 标志,供 tool 循环 / callback 主动轮询</li>
 *   <li>{@link Disposable#dispose()} 断 reactor Flux(Spring AI stream 用)</li>
 *   <li>{@link Thread#interrupt()} 绑定线程(老路径,blockLast 兜底)</li>
 * </ol>
 */
@Slf4j
@Getter
public class CancellationHandle {

    private final String streamId;

    private final long messageId;

    private final long sessionId;

    private final String nodeId;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile Thread runner;

    private volatile Disposable disposable;

    public CancellationHandle(String streamId, long messageId, long sessionId, String nodeId) {
        this.streamId = streamId;
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.nodeId = nodeId;
    }

    /** 绑定执行线程,cancel 时会被 interrupt。通常是 provider 调用线程。 */
    public void bindThread(Thread thread) {
        this.runner = thread;
    }

    /** 绑定 reactor {@link Disposable}(Spring AI Flux 订阅),cancel 时优先 dispose。 */
    public void bindDisposable(Disposable d) {
        this.disposable = d;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 标记为已取消,dispose reactor subscription + interrupt 关联线程(若已绑定)。
     * 返回是否第一次成功标记(用于避免重复落库)。
     */
    public boolean doCancel() {
        boolean first = cancelled.compareAndSet(false, true);
        if (first) {
            Disposable d = this.disposable;
            if (d != null && !d.isDisposed()) {
                try {
                    d.dispose();
                } catch (Exception e) {
                    log.debug("dispose stream {} error: {}", streamId, e.getMessage());
                }
            }
            if (runner != null) {
                runner.interrupt();
            }
        }
        return first;
    }
}
