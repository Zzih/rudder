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

package io.github.zzih.rudder.common.utils.io;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/** 读取 classpath 文本资源。AI dialect / context 模板加载专用。 */
public final class ClasspathResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(ClasspathResourceUtils.class);

    private ClasspathResourceUtils() {
    }

    /** 读 classpath 下的 UTF-8 文本;找不到或失败返回空串并 warn。 */
    public static String readTextOrEmpty(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) {
                log.warn("classpath resource {} missing", path);
                return "";
            }
            return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("failed to read classpath resource {}: {}", path, e.getMessage());
            return "";
        }
    }
}
