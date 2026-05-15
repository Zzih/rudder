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
import io.github.zzih.rudder.service.coordination.registry.NodeAddress;
import io.github.zzih.rudder.service.coordination.registry.NodeRegistryService;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.registry.dto.ServiceRegistryDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 节点注册与心跳。Liveness 真理来源 = Redis TTL,DB 仅做事件历史与 UI 展示。
 *
 * <p>所有 Redis 操作走 {@link NodeRegistryService} 协调原语,业务层不直接持 Template。
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
    private final NodeRegistryService nodeRegistry;
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

        publishHeartbeat();

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
        publishHeartbeat();
    }

    @PreDestroy
    public void deregister() {
        if (serviceType == null) {
            return;
        }
        nodeRegistry.revoke(serviceType, localHost, rpcPort);
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
        return nodeRegistry.aliveByType(ServiceType.EXECUTION);
    }

    /** DB task_count 仅终态写入,ONLINE 行以 Redis 实时值为准。 */
    public List<ServiceRegistryDTO> listAllDetail() {
        List<ServiceRegistryDTO> rows = BeanConvertUtils.convertList(
                serviceRegistryDao.selectAll(), ServiceRegistryDTO.class);
        Map<String, Integer> liveCounts = liveCountsByRpcAddress();
        for (ServiceRegistryDTO row : rows) {
            if (row.getStatus() == ServiceStatus.ONLINE) {
                Integer live = liveCounts.get(row.getHost() + ":" + row.getPort());
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

    private void publishHeartbeat() {
        int count = taskCountProvider.getIfAvailable(() -> TaskCountProvider.ZERO).currentRunningCount();
        nodeRegistry.publish(serviceType, localHost, rpcPort, count, NODE_TTL);
    }

    private Map<String, Integer> liveCountsByRpcAddress() {
        List<NodeAddress> all = nodeRegistry.aliveAll();
        Map<String, Integer> map = new HashMap<>(all.size());
        for (NodeAddress n : all) {
            map.put(n.rpcAddress(), n.taskCount());
        }
        return map;
    }

    private NodeInfo selfInfo() {
        return new NodeInfo(serviceType.name(), localHost, rpcPort);
    }
}
