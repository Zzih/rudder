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

import static io.github.zzih.rudder.notification.api.model.NotificationExtraKeys.*;

import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.common.utils.net.NetUtils;
import io.github.zzih.rudder.dao.dao.ServiceRegistryDao;
import io.github.zzih.rudder.dao.entity.ServiceRegistry;
import io.github.zzih.rudder.dao.enums.ServiceStatus;
import io.github.zzih.rudder.dao.enums.ServiceType;
import io.github.zzih.rudder.notification.api.model.NotificationEventType;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.NotificationMessage;
import io.github.zzih.rudder.rpc.spring.RpcProperties;
import io.github.zzih.rudder.service.notification.NotificationService;
import io.github.zzih.rudder.service.registry.dto.ServiceRegistryDTO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRegistryService {

    private final ServiceRegistryDao serviceRegistryDao;
    private final RpcProperties rpcProperties;
    private final NotificationService notificationService;

    @Value("${rudder.service.type:SERVER}")
    private String serviceTypeStr;

    @Value("${rudder.service.heartbeat-timeout:30}")
    private int heartbeatTimeoutSeconds;

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

        if (existing != null) {
            existing.setStartTime(now);
            existing.setHeartbeat(now);
            existing.setStatus(ServiceStatus.ONLINE);
            existing.setTaskCount(0);
            serviceRegistryDao.updateById(existing);
        } else {
            ServiceRegistry reg = new ServiceRegistry();
            reg.setType(serviceType);
            reg.setHost(localHost);
            reg.setPort(rpcPort);
            reg.setStartTime(now);
            reg.setHeartbeat(now);
            reg.setStatus(ServiceStatus.ONLINE);
            reg.setTaskCount(0);
            serviceRegistryDao.insert(reg);
        }

        log.info("Service registered: type={}, rpc={}:{}", serviceType, localHost, rpcPort);
        notifySelf(NotificationEventType.NODE_ONLINE, "Rudder Node Online",
                "Node came online: ", NotificationLevel.SUCCESS);
    }

    /**
     * 心跳:正常路径单 UPDATE 完成,只在被 cleanExpired 误判翻成 OFFLINE 后(网络抖动场景)
     * 才走复活路径并补发 NODE_ONLINE 通知。
     * <p>
     * 拆成 if-online vs revive 两步是为了把"我以为我活着但已被宣告死亡"这种边界状态显式化 —
     * 单 UPDATE 不区分这两种语义,无法精准发通知。
     */
    @Scheduled(fixedRate = 10_000)
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        int affected = serviceRegistryDao.updateHeartbeatIfOnline(serviceType, localHost, rpcPort, now);
        if (affected > 0) {
            return;
        }
        // 0 行:status 不是 ONLINE(被 cleanExpired 翻成 OFFLINE 或行被外部删了)
        ServiceRegistry existing = serviceRegistryDao.selectByTypeAndHostAndPort(serviceType, localHost, rpcPort);
        if (existing == null) {
            // 极端:DB row 没了,重走一遍 register 把行补回来
            log.warn("Service registry row missing, re-registering: type={}, rpc={}:{}",
                    serviceType, localHost, rpcPort);
            register();
            return;
        }
        serviceRegistryDao.reviveAndUpdateHeartbeat(serviceType, localHost, rpcPort, now);
        log.warn("Service revived from OFFLINE: type={}, rpc={}:{}", serviceType, localHost, rpcPort);
        notifySelf(NotificationEventType.NODE_ONLINE, "Rudder Node Recovered",
                "Node came back online: ", NotificationLevel.SUCCESS);
    }

    /**
     * 清理过期节点。多节点同时跑这个 Scheduled,如果不做 CAS 会每个 alive 节点都给同一个 dead
     * 节点发一条通知 — 改成对每个候选独立调 markOfflineIfOnline (UPDATE ... AND status='ONLINE'),
     * 只有 affected=1 的节点(本节点抢到了翻转)才入通知列表。
     */
    @Scheduled(fixedRate = 15_000)
    public void cleanExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);
        List<ServiceRegistry> candidates = serviceRegistryDao.selectExpiredOnline(threshold);
        if (candidates.isEmpty()) {
            return;
        }

        List<ServiceRegistry> flipped = new java.util.ArrayList<>();
        for (ServiceRegistry node : candidates) {
            int affected = serviceRegistryDao.markOfflineIfOnline(node.getType(), node.getHost(), node.getPort());
            if (affected > 0) {
                flipped.add(node);
            }
        }
        if (flipped.isEmpty()) {
            return;
        }

        log.warn("Marked {} expired service(s) as OFFLINE", flipped.size());
        String nodeList = flipped.stream()
                .map(n -> n.getType() + " " + n.getHost() + ":" + n.getPort())
                .collect(Collectors.joining("\n"));
        notifyNodeEvent(flipped, NotificationEventType.NODE_OFFLINE,
                "Rudder Node Offline Alert", "The following node(s) went offline:\n" + nodeList,
                NotificationLevel.ERROR);
    }

    private void notifyNodeEvent(List<ServiceRegistry> nodes, NotificationEventType eventType,
                                 String title, String content, NotificationLevel level) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put(EVENT_TYPE, eventType.name());
        extra.put(NODE_COUNT, String.valueOf(nodes.size()));
        for (int i = 0; i < nodes.size(); i++) {
            ServiceRegistry n = nodes.get(i);
            extra.put(nodeType(i), n.getType().name());
            extra.put(nodeHost(i), n.getHost());
            extra.put(nodePort(i), String.valueOf(n.getPort()));
        }
        NotificationMessage message = NotificationMessage.builder()
                .title(title).content(content).level(level).extra(extra).build();
        notificationService.notify(message, eventType, null);
    }

    /** 自身节点事件 — register / heartbeat-revive / deregister 都用,内容统一拼 type+host:port。 */
    private void notifySelf(NotificationEventType eventType, String title, String contentPrefix,
                            NotificationLevel level) {
        ServiceRegistry self = new ServiceRegistry();
        self.setType(serviceType);
        self.setHost(localHost);
        self.setPort(rpcPort);
        notifyNodeEvent(List.of(self), eventType, title,
                contentPrefix + serviceType + " " + localHost + ":" + rpcPort, level);
    }

    /**
     * 优雅下线。CAS markOfflineIfOnline:只有从 ONLINE 翻 OFFLINE 才发通知 — 避免和
     * cleanExpired 同时翻转造成重复通知(那条通道会自己发)。
     */
    @PreDestroy
    public void deregister() {
        int affected = serviceRegistryDao.markOfflineIfOnline(serviceType, localHost, rpcPort);
        log.info("Service deregistered: type={}, rpc={}:{}, flipped={}",
                serviceType, localHost, rpcPort, affected);
        if (affected > 0) {
            notifySelf(NotificationEventType.NODE_OFFLINE, "Rudder Node Offline",
                    "Node went offline (graceful shutdown): ", NotificationLevel.WARN);
        }
    }

    /** Controller 用:列所有 service 实例(DTO 形态),避免直连 DAO。 */
    public List<ServiceRegistryDTO> listAllDetail() {
        return BeanConvertUtils.convertList(serviceRegistryDao.selectAll(), ServiceRegistryDTO.class);
    }

    public List<ServiceRegistry> getOnlineExecutions() {
        return serviceRegistryDao.selectOnlineByType(ServiceType.EXECUTION);
    }

    /**
     * 本机 RPC 地址（host:port），供回调和内部通信使用。
     */
    public String getLocalRpcAddress() {
        return localHost + ":" + rpcPort;
    }

}
