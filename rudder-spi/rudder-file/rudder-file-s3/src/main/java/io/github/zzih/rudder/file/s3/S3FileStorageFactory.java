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

package io.github.zzih.rudder.file.s3;

import io.github.zzih.rudder.file.api.FileStorage;
import io.github.zzih.rudder.file.api.spi.FileStorageFactory;
import io.github.zzih.rudder.spi.api.context.ProviderContext;
import io.github.zzih.rudder.spi.api.model.PluginParamDefinition;

import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(FileStorageFactory.class)
public class S3FileStorageFactory implements FileStorageFactory<S3FileStorageProperties> {

    @Override
    public String getProvider() {
        return "S3";
    }

    @Override
    public Class<S3FileStorageProperties> propertiesClass() {
        return S3FileStorageProperties.class;
    }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
                PluginParamDefinition.builder()
                        .name("region").label("Region").type("input")
                        .required(true).placeholder("us-east-1")
                        .build(),
                PluginParamDefinition.builder()
                        .name("accessKeyId").label("Access Key ID").type("input")
                        .required(true).placeholder("AKIA...")
                        .build(),
                PluginParamDefinition.builder()
                        .name("secretAccessKey").label("Secret Access Key").type("password")
                        .required(true).placeholder("your-secret-key")
                        .build(),
                PluginParamDefinition.builder()
                        .name("bucket").label("Bucket").type("input")
                        .required(true).placeholder("my-rudder-bucket")
                        .build(),
                PluginParamDefinition.builder()
                        .name("basePath").label("Base Path").type("input")
                        .required(false).placeholder("/rudder")
                        .defaultValue("/rudder")
                        .build());
    }

    @Override
    public FileStorage create(ProviderContext ctx, S3FileStorageProperties props) {
        return new S3FileStorage(
                props.region(), props.accessKeyId(), props.secretAccessKey(),
                props.bucket(), props.basePath());
    }
}
