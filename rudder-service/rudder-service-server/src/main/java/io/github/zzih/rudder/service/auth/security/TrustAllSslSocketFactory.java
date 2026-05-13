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

package io.github.zzih.rudder.service.auth.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 用于 LDAP {@code java.naming.ldap.factory.socket}:JNDI 通过反射调 {@code getDefault()} 拿
 * SocketFactory 实例。跳过证书校验,**仅供 ldaps 开发/测试**,生产应导入企业 CA。
 *
 * <p>由 {@code LdapSourceConfigData.trustAllCerts=true} 触发;失败模式下日志已 WARN。
 */
public final class TrustAllSslSocketFactory extends SSLSocketFactory {

    private static final TrustAllSslSocketFactory INSTANCE = new TrustAllSslSocketFactory();

    private final SSLSocketFactory delegate;

    public TrustAllSslSocketFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                    new X509TrustManager() {

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            // trust-all 模式不校验
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            // trust-all 模式不校验
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, null);
            this.delegate = ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build trust-all SSL socket factory", e);
        }
    }

    public static SocketFactory getDefault() {
        return INSTANCE;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                               int localPort) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
