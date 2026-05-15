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

package io.github.zzih.rudder.service.registry;

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.net.NetUtils;
import io.github.zzih.rudder.dao.dao.ServiceRegistryDao;
import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.dao.enums.ServiceStatus;
import io.github.zzih.rudder.dao.enums.ServiceType;
import io.github.zzih.rudder.notification.api.model.NodeInfo;
import io.github.zzih.rudder.notification.api.model.NodeOfflineMessage;
import io.github.zzih.rudder.notification.api.model.NodeOnlineMessage;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.rpc.spring.RpcProperties;
import io.github.zzih.rudder.service.coordination.RedisNaming;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.registry.dto.ServiceRegistryDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 节点注册与心跳。Liveness 由 Redis TTL 表达,DB 仅做事件历史与 UI 展示。
 *
 * <p>Redis value = taskCount(整数 string),由 {@link TaskCountProvider} 在心跳时填入。
 * NodeOnline 通知绑 DB 状态翻转,重启时 DB 仍 ONLINE 不重发,避免 crash loop 噪音。
 * OFFLINE 翻转见 {@link RegistryReconciler}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRegistryService {

    private static final Duration NODE_TTL = Duration.ofSeconds(30);

    private final ServiceRegistryDao serviceRegistryDao;
    private final RpcProperties rpcProperties;
    private final NotificationService notificationService;
    private final StringRedisTemplate redis;
    private final ObjectProvider<TaskCountProvider> taskCountProvider;

    @Value("${rudder.service.type:SERVER}")
    private String serviceTypeStr;

    private ServiceType serviceType;
    private String localHost;
    private int rpcPort;

    @PostConstruct
    public void register() {
        serviceType = ServiceType.valueOf(serviceTypeStr);
        rpcPort = rpcProperties.getPort();
        localHost = NetUtils.getLocalIp();

        refreshSelfTtl();

        LocalDateTime now = LocalDateTime.now();
        ServiceRegistry existing = serviceRegistryDao.selectByTypeAndHostAndPort(serviceType, localHost, rpcPort);
        boolean flipped;
        if (existing == null) {
            ServiceRegistry reg = new ServiceRegistry();
            reg.setType(serviceType);
            reg.setHost(localHost);
            reg.setPort(rpcPort);
            reg.setStartTime(now);
            reg.setHeartbeat(now);
            reg.setStatus(ServiceStatus.ONLINE);
            reg.setTaskCount(0);
            serviceRegistryDao.insert(reg);
            flipped = true;
        } else {
            flipped = existing.getStatus() != ServiceStatus.ONLINE;
            existing.setStartTime(now);
            existing.setHeartbeat(now);
            existing.setStatus(ServiceStatus.ONLINE);
            existing.setTaskCount(0);
            serviceRegistryDao.updateById(existing);
        }

        log.info("Service registered: type={}, rpc={}:{}, flipped={}",
                serviceType, localHost, rpcPort, flipped);
        if (flipped) {
            notificationService.notify(NodeOnlineMessage.builder()
                    .level(NotificationLevel.SUCCESS)
                    .nodes(List.of(selfInfo()))
                    .build());
        }
    }

    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        if (serviceType == null) {
            return;
        }
        refreshSelfTtl();
    }

    @PreDestroy
    public void deregister() {
        if (serviceType == null) {
            return;
        }
        redis.delete(nodeKeyOf(serviceType, localHost, rpcPort));
        int affected = serviceRegistryDao.markOfflineIfOnline(
                serviceType, localHost, rpcPort, LocalDateTime.now());
        log.info("Service deregistered: type={}, rpc={}:{}, flipped={}",
                serviceType, localHost, rpcPort, affected);
        if (affected > 0) {
            notificationService.notify(NodeOfflineMessage.builder()
                    .level(NotificationLevel.WARN)
                    .nodes(List.of(selfInfo()))
                    .graceful(true)
                    .build());
        }
    }

    public List<NodeAddress> getOnlineExecutions() {
        return scanNodes(RedisNaming.Registry.NODE_PREFIX + ServiceType.EXECUTION.name() + ":*");
    }

    /** DB task_count 仅终态写入,ONLINE 行以 Redis 实时值为准。 */
    public List<ServiceRegistryDTO> listAllDetail() {
        List<ServiceRegistryDTO> rows = BeanConvertUtils.convertList(
                serviceRegistryDao.selectAll(), ServiceRegistryDTO.class);
        Map<String, Integer> liveCounts = liveCountsByRpcAddress();
        for (ServiceRegistryDTO row : rows) {
            if (row.getStatus() == ServiceStatus.ONLINE) {
                Integer live = liveCounts.get(rpcAddressOf(row.getHost(), row.getPort()));
                if (live != null) {
                    row.setTaskCount(live);
                }
            }
        }
        return rows;
    }

    public String getLocalRpcAddress() {
        return localHost + ":" + rpcPort;
    }

    Set<String> scanAllAliveKeys() {
        Set<String> keys = new HashSet<>();
        scanKeys(RedisNaming.Registry.NODE_PREFIX + "*", keys::add);
        return keys;
    }

    static String nodeKeyOf(ServiceType type, String host, int port) {
        return RedisNaming.Registry.NODE_PREFIX + type.name() + ":" + host + ":" + port;
    }

    static NodeAddress parseNodeKey(String key, String value) {
        if (!key.startsWith(RedisNaming.Registry.NODE_PREFIX)) {
            return null;
        }
        String rest = key.substring(RedisNaming.Registry.NODE_PREFIX.length());
        // 格式:{TYPE}:{HOST}:{PORT}。type 在首段,port 在末段,中段可含 ":" 以兼容 IPv6 host。
        int firstColon = rest.indexOf(':');
        int lastColon = rest.lastIndexOf(':');
        if (firstColon < 0 || lastColon <= firstColon) {
            return null;
        }
        try {
            ServiceType type = ServiceType.valueOf(rest.substring(0, firstColon));
            String host = rest.substring(firstColon + 1, lastColon);
            int port = Integer.parseInt(rest.substring(lastColon + 1));
            return new NodeAddress(type, host, port, parseTaskCount(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    void notifyOffline(Collection<NodeInfo> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        notificationService.notify(NodeOfflineMessage.builder()
                .level(NotificationLevel.ERROR)
                .nodes(List.copyOf(nodes))
                .graceful(false)
                .build());
    }

    private List<NodeAddress> scanNodes(String pattern) {
        List<String> keys = new ArrayList<>();
        scanKeys(pattern, keys::add);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        List<NodeAddress> out = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            NodeAddress addr = parseNodeKey(keys.get(i), values == null ? null : values.get(i));
            if (addr != null) {
                out.add(addr);
            }
        }
        return out;
    }

    private Map<String, Integer> liveCountsByRpcAddress() {
        List<NodeAddress> all = scanNodes(RedisNaming.Registry.NODE_PREFIX + "*");
        Map<String, Integer> map = new HashMap<>(all.size());
        for (NodeAddress n : all) {
            map.put(n.rpcAddress(), n.taskCount());
        }
        return map;
    }

    private void refreshSelfTtl() {
        int count = taskCountProvider.getIfAvailable(() -> TaskCountProvider.ZERO).currentRunningCount();
        redis.opsForValue().set(nodeKeyOf(serviceType, localHost, rpcPort),
                String.valueOf(count), NODE_TTL);
    }

    private static String rpcAddressOf(String host, int port) {
        return host + ":" + port;
    }

    private void scanKeys(String pattern, Consumer<String> consumer) {
        try (
                Cursor<String> cursor = redis.scan(
                        ScanOptions.scanOptions().match(pattern).count(200).build())) {
            cursor.forEachRemaining(consumer);
        }
    }

    private static int parseTaskCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private NodeInfo selfInfo() {
        return new NodeInfo(serviceType.name(), localHost, rpcPort);
    }
}
