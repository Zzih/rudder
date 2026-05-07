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

package io.github.zzih.rudder.common.utils.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import lombok.extern.slf4j.Slf4j;

/**
 * 本机 IP / RPC 地址解析。{@link io.github.zzih.rudder.script.service.ServiceRegistryService} 注册用。
 */
@Slf4j
public final class NetUtils {

    private static volatile String cachedLocalIp;

    private NetUtils() {
    }

    /** 获取本机局域网 IP(优先非回环 IPv4)。结果缓存,多次调用不重复探测。 */
    public static String getLocalIp() {
        if (cachedLocalIp != null) {
            return cachedLocalIp;
        }
        synchronized (NetUtils.class) {
            if (cachedLocalIp != null) {
                return cachedLocalIp;
            }
            String override = System.getenv("RUDDER_LOCAL_IP");
            if (override != null && !override.isBlank()) {
                cachedLocalIp = override.trim();
            } else {
                cachedLocalIp = doGetLocalIp();
            }
            log.info("Local IP resolved: {}", cachedLocalIp);
            return cachedLocalIp;
        }
    }

    /** 获取本机 RPC 地址,格式 host:port。 */
    public static String getLocalAddress(int port) {
        return getLocalIp() + ":" + port;
    }

    private static String doGetLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate network interfaces: {}", e.getMessage());
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to get local host address: {}", e.getMessage());
        }
        return "127.0.0.1";
    }
}
