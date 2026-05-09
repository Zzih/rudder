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

package io.github.zzih.rudder.file.oss;

public record OssFileStorageProperties(
        String endpoint,
        String accessKeyId,
        String accessKeySecret,
        String bucket,
        String basePath) {

    public OssFileStorageProperties {
        if (endpoint == null) {
            endpoint = "";
        }
        if (accessKeyId == null) {
            accessKeyId = "";
        }
        if (accessKeySecret == null) {
            accessKeySecret = "";
        }
        if (bucket == null) {
            bucket = "";
        }
        if (basePath == null || basePath.isBlank()) {
            basePath = "/rudder";
        }
    }
}
