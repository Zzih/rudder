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

package io.github.zzih.rudder.datasource.service;

import io.github.zzih.rudder.common.enums.error.DatasourceErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.crypto.CryptoUtils;
import io.github.zzih.rudder.common.utils.json.JsonUtils;
import io.github.zzih.rudder.datasource.model.DataSourceCredentials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CredentialService {

    @Value("${rudder.security.encrypt-key:}")
    private String encryptKey;

    /**
     * 将凭证序列化为 JSON，AES 加密后 Base64 编码。
     */
    public String encrypt(DataSourceCredentials credentials) {
        try {
            String json = JsonUtils.toJson(credentials);
            return CryptoUtils.aesEncrypt(json, encryptKey);
        } catch (Exception e) {
            log.error("凭证加密失败", e);
            throw new BizException(DatasourceErrorCode.DS_CRED_ENCRYPT_FAILED);
        }
    }

    /**
     * Base64 解码，AES 解密，然后从 JSON 反序列化凭证。
     */
    public DataSourceCredentials decrypt(String encrypted) {
        try {
            String json = CryptoUtils.aesDecrypt(encrypted, encryptKey);
            return JsonUtils.fromJson(json, DataSourceCredentials.class);
        } catch (Exception e) {
            log.error("凭证解密失败", e);
            throw new BizException(DatasourceErrorCode.DS_CRED_DECRYPT_FAILED);
        }
    }
}
