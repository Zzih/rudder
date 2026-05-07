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

package io.github.zzih.rudder.common.page;

import java.io.Serializable;

import lombok.Data;

@Data
public class PageRequest implements Serializable {

    /** 全局分页上限,防止恶意 / 误传巨大 pageSize 触发 OOM。 */
    public static final int MAX_PAGE_SIZE = 200;

    public static final int DEFAULT_PAGE_SIZE = 20;

    private int pageNum = 1;

    private int pageSize = DEFAULT_PAGE_SIZE;

    public long offset() {
        return (long) (pageNum - 1) * pageSize;
    }

    /** 把外部传入的 pageSize 收敛到 [1, MAX_PAGE_SIZE];非正数走默认。 */
    public static int normalizePageSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /** 把外部传入的 pageNum 收敛到 [1, +∞)。 */
    public static int normalizePageNum(int requested) {
        return Math.max(requested, 1);
    }
}
