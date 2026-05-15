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
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.registry.dto.ServiceRegistryDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 节点注册与心跳。DB 为单一真理源,每节点自报 heartbeat,心跳 tick 顺带扫 stale 行并以 DB
 * 行级锁 + CAS 串行化翻转,翻成功者独占发通知,避免重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRegistryService {

    private static final Duration STALE_AFTER = Duration.ofSeconds(30);
    private static final Duration CLEANUP_AFTER = Duration.ofHours(1);

    private final ServiceRegistryDao serviceRegistryDao;
    private final RpcProperties rpcProperties;
    private final NotificationService notificationService;
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

        LocalDateTime now = LocalDateTime.now();
        ServiceRegistry existing = serviceRegistryDao.selectByTypeAndHostAndPort(serviceType, localHost, rpcPort);
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
        } else {
            existing.setStartTime(now);
            existing.setHeartbeat(now);
            existing.setStatus(ServiceStatus.ONLINE);
            existing.setTaskCount(0);
            serviceRegistryDao.updateById(existing);
        }

        log.info("Service registered: type={}, rpc={}:{}", serviceType, localHost, rpcPort);
        notificationService.notify(NodeOnlineMessage.builder()
                .level(NotificationLevel.SUCCESS)
                .nodes(List.of(selfInfo()))
                .build());
    }

    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        if (serviceType == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            int count = taskCountProvider.getIfAvailable(() -> TaskCountProvider.ZERO).currentRunningCount();
            serviceRegistryDao.updateHeartbeat(serviceType, localHost, rpcPort, now, count);
            reapStaleNodes(now);
            cleanupOldOffline(now);
        } catch (Exception e) {
            log.warn("Heartbeat tick error", e);
        }
    }

    @PreDestroy
    public void deregister() {
        if (serviceType == null) {
            return;
        }
        int affected = serviceRegistryDao.markOfflineIfOnline(
                serviceType, localHost, rpcPort, LocalDateTime.now());
        log.info("Service deregistered: type={}, rpc={}:{}, flipped={}",
                serviceType, localHost, rpcPort, affected);
        if (affected > 0) {
            notifyOffline(List.of(selfInfo()), true, NotificationLevel.WARN);
        }
    }

    public List<NodeAddress> getOnlineExecutions() {
        List<ServiceRegistry> rows = serviceRegistryDao.selectOnlineByType(ServiceType.EXECUTION);
        List<NodeAddress> out = new ArrayList<>(rows.size());
        for (ServiceRegistry r : rows) {
            int count = r.getTaskCount() != null ? r.getTaskCount() : 0;
            out.add(new NodeAddress(r.getType(), r.getHost(), r.getPort(), count));
        }
        return out;
    }

    public List<ServiceRegistryDTO> listAllDetail() {
        return BeanConvertUtils.convertList(serviceRegistryDao.selectAll(), ServiceRegistryDTO.class);
    }

    public String getLocalRpcAddress() {
        return localHost + ":" + rpcPort;
    }

    // SELECT-then-CAS 而非批量 UPDATE:为保留 attribution(哪些 row 是本节点翻的,只发对应通知)。
    private void reapStaleNodes(LocalDateTime now) {
        LocalDateTime threshold = now.minus(STALE_AFTER);
        List<ServiceRegistry> stale = serviceRegistryDao.selectStaleOnline(threshold);
        if (stale.isEmpty()) {
            return;
        }
        List<NodeInfo> flipped = new ArrayList<>();
        for (ServiceRegistry row : stale) {
            int affected = serviceRegistryDao.markOfflineIfStale(row.getId(), threshold, now);
            if (affected > 0) {
                flipped.add(new NodeInfo(row.getType().name(), row.getHost(), row.getPort()));
            }
        }
        if (!flipped.isEmpty()) {
            log.warn("Reaped {} stale ONLINE node(s) to OFFLINE", flipped.size());
            notifyOffline(flipped, false, NotificationLevel.ERROR);
        }
    }

    private void cleanupOldOffline(LocalDateTime now) {
        int deleted = serviceRegistryDao.deleteOfflineOlderThan(now.minus(CLEANUP_AFTER));
        if (deleted > 0) {
            log.info("Cleaned up {} old OFFLINE registry row(s)", deleted);
        }
    }

    private void notifyOffline(List<NodeInfo> nodes, boolean graceful, NotificationLevel level) {
        notificationService.notify(NodeOfflineMessage.builder()
                .level(level)
                .nodes(nodes)
                .graceful(graceful)
                .build());
    }

    private NodeInfo selfInfo() {
        return new NodeInfo(serviceType.name(), localHost, rpcPort);
    }
}
