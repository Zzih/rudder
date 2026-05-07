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

import java.nio.file.Path;

/** 路径穿越防护。RPC 端 {@code fetchLog/fetchResult} 等读本地文件的接口使用。 */
public final class PathSecurityUtils {

    private PathSecurityUtils() {
    }

    /**
     * 解析 {@code relativePath} 并确保落在 {@code baseDir} 内部。
     * 任何导致越界(如 {@code ../../etc/passwd})的输入抛 {@link SecurityException}。
     */
    public static Path resolveWithinBase(Path baseDir, String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("relativePath must not be null");
        }
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("Path outside base dir: " + relativePath);
        }
        return resolved;
    }
}
