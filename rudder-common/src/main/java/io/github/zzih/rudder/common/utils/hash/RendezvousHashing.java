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

package io.github.zzih.rudder.common.utils.hash;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Function;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/** 节点 churn 时只重分配 ~1/N 的 key,无需虚拟节点 / 环结构。 */
public final class RendezvousHashing {

    private static final HashFunction HASH = Hashing.murmur3_128();

    private RendezvousHashing() {
    }

    /**
     * 在 candidates 内为 key 确定性选定一个节点。tie 时按 idFn 输出字典序更大者获胜。
     *
     * @return 选中的节点,candidates 为空返回 {@code null}
     */
    public static <T> T pick(long key, Collection<T> candidates, Function<T, String> idFn) {
        T winner = null;
        long maxHash = 0L;
        String winnerId = null;
        for (T node : candidates) {
            String id = idFn.apply(node);
            long h = HASH.newHasher()
                    .putLong(key)
                    .putByte((byte) ':')
                    .putString(id, StandardCharsets.UTF_8)
                    .hash()
                    .asLong();
            boolean better = winner == null || h > maxHash || (h == maxHash && id.compareTo(winnerId) > 0);
            if (better) {
                maxHash = h;
                winner = node;
                winnerId = id;
            }
        }
        return winner;
    }
}
