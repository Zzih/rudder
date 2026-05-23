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

package io.github.zzih.rudder.service.dataperm.client.ranger;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link RangerAdminRestClient} 的 RestTemplate 实现。
 *
 * <p>**RestTemplate 单例**:每次调用先从 {@link DataPermConfigService} 拿最新 config 注入 Basic Auth header,
 * 配置 hot reload 后下一次调用就用新凭证,不必重建 client。
 *
 * <p>**错误码映射**:
 * <ul>
 *   <li>404(查 service / 删 policy):静默 fallback,不抛</li>
 *   <li>5xx / 网络不可达:{@code RANGER_UNREACHABLE}</li>
 *   <li>4xx(其他):{@code RANGER_POLICY_CONFLICT}(常见为参数错 / 已存在)</li>
 * </ul>
 */
@Slf4j
@Component
public class RangerAdminRestClientImpl implements RangerAdminRestClient {

    private static final String PATH_POLICY = "/service/public/v2/api/policy";
    private static final String PATH_POLICY_BY_ID = "/service/public/v2/api/policy/{id}";
    private static final String PATH_SERVICE_BY_NAME = "/service/public/v2/api/service/name/{name}";
    private static final String PATH_SERVICEDEF_BY_NAME = "/service/public/v2/api/servicedef/name/{name}";
    private static final String PATH_POLICIES_BY_SERVICE = "/service/public/v2/api/policy";
    private static final String PATH_USERS = "/service/xusers/users";

    private final DataPermConfigService configService;
    private final RestTemplate restTemplate;

    /** serviceExists 短期缓存,Reconciler 每轮跨多 service 调用前置 check;service 删改频次低,60s 足够。 */
    private final Cache<String, Boolean> serviceExistsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(256)
            .build();

    /** ensureUser 幂等结果缓存:Ranger 端 user 一经存在几乎不删,10min TTL 避免每轮 reconcile 重复 POST。 */
    private final Cache<String, Boolean> userEnsuredCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(4096)
            .build();

    @Autowired
    public RangerAdminRestClientImpl(DataPermConfigService configService) {
        this(configService, defaultRestTemplate(orDefault(configService.active().getRangerAdminTimeoutMs(), 10_000)));
    }

    /** 测试钩子(包内可见):MockRestServiceServer 用。 */
    RangerAdminRestClientImpl(DataPermConfigService configService, RestTemplate restTemplate) {
        this.configService = configService;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate defaultRestTemplate(int timeoutMs) {
        int t = Math.max(1_000, timeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);
        return new RestTemplate(factory);
    }

    @Override
    public void healthCheck() {
        // Reconciler 周期检测,4xx 容忍 — 凭证 / 路径错由具体 CRUD 调用抛出
        DataPermConfigDTO config = requireEnabledAndConfigured();
        doHealthCheck(config, restTemplate, false);
    }

    /**
     * Stateless 严格探活 — 直接用传入 config 调用 Ranger Admin,不需要 configService;
     * 4xx 也视为失败(401 凭证错 / 403 鉴权失败 / 404 路径错 都直接抛错给前端)。
     * 供 ConfigController.testDataPermConnection 在保存前预检用。
     */
    public static void probeHealthCheck(DataPermConfigDTO config) {
        if (config.getRangerAdminUrl() == null || config.getRangerAdminUrl().isBlank()) {
            throw new BizException(DataPermErrorCode.RANGER_UNREACHABLE, "rangerAdminUrl not configured");
        }
        doHealthCheck(config, defaultRestTemplate(orDefault(config.getRangerAdminTimeoutMs(), 10_000)), true);
    }

    private static void doHealthCheck(DataPermConfigDTO config, RestTemplate restTemplate, boolean strict) {
        // GET /service/public/v2/api/policy?pageSize=1 — Ranger Admin 标准 public v2 endpoint,
        // 返回空数组或一条 policy。200 = Admin 可达且凭证有效。
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path("/service/public/v2/api/policy")
                .queryParam("pageSize", 1)
                .build()
                .toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders(config)), String.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unreachable(e);
        } catch (HttpClientErrorException e) {
            if (strict) {
                throw unreachable(e);
            }
            log.debug("Ranger healthCheck got 4xx (ignored): {}", e.getStatusCode());
        }
    }

    /** 把 Ranger HTTP / 网络异常归一为带详细消息的 RANGER_UNREACHABLE,保留 cause 给日志,详情透前端。 */
    private static BizException unreachable(Throwable e) {
        String detail;
        if (e instanceof HttpStatusCodeException hsce) {
            detail = hsce.getStatusCode() + " " + hsce.getStatusText();
        } else if (e instanceof ResourceAccessException) {
            Throwable root = ((ResourceAccessException) e).getMostSpecificCause();
            root = root != null ? root : e;
            detail = root.getClass().getSimpleName() + ": " + root.getMessage();
        } else {
            detail = e.getMessage();
        }
        return new BizException(DataPermErrorCode.RANGER_UNREACHABLE, e, detail);
    }

    @Override
    public boolean serviceExists(String serviceName) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path(PATH_SERVICE_BY_NAME)
                .buildAndExpand(serviceName).toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders(config)), String.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unreachable(e);
        }
    }

    @Override
    public RangerPolicy createPolicy(RangerPolicy policy) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = URI.create(baseUrl(config) + PATH_POLICY);
        HttpEntity<RangerPolicy> entity = new HttpEntity<>(policy, jsonHeaders(config));
        return retryOnTransient(() -> {
            try {
                return restTemplate.exchange(uri, HttpMethod.POST, entity, RangerPolicy.class).getBody();
            } catch (HttpClientErrorException e) {
                DataPermErrorCode code = isResourceConflict(e)
                        ? DataPermErrorCode.RANGER_POLICY_RESOURCE_CONFLICT
                        : DataPermErrorCode.RANGER_POLICY_CONFLICT;
                throw new BizException(code, e, formatRangerError(e));
            }
        });
    }

    /** Ranger (service, resource) 唯一冲突回 HTTP 400 + body error code[3010];仅此模式让上层走 takeover。 */
    private static boolean isResourceConflict(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return body != null
                && (body.contains("error code[3010]")
                        || body.contains("Another policy already exists for matching resource"));
    }

    @Override
    public RangerPolicy updatePolicy(Long policyId, RangerPolicy policy) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path(PATH_POLICY_BY_ID).buildAndExpand(policyId).toUri();
        HttpEntity<RangerPolicy> entity = new HttpEntity<>(policy, jsonHeaders(config));
        return retryOnTransient(() -> {
            try {
                return restTemplate.exchange(uri, HttpMethod.PUT, entity, RangerPolicy.class).getBody();
            } catch (HttpClientErrorException e) {
                throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
            }
        });
    }

    @Override
    public void deletePolicy(Long policyId) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path(PATH_POLICY_BY_ID).buildAndExpand(policyId).toUri();
        retryOnTransient(() -> {
            try {
                restTemplate.exchange(uri, HttpMethod.DELETE, new HttpEntity<>(authHeaders(config)), Void.class);
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("Ranger policy {} already deleted (404 ignored)", policyId);
            } catch (HttpClientErrorException e) {
                throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
            }
            return null;
        });
    }

    /**
     * 写路径 transient 错误重试(5xx / 网络抖动);4xx 一律不重试(语义冲突 / 凭证错应立即透出)。
     * 2 次重试 200ms + 1s,与 reconciler 主循环并发写线程池配合,单个 change 失败不连累整轮。
     */
    private <T> T retryOnTransient(java.util.function.Supplier<T> op) {
        long[] backoff = {200, 1_000};
        for (int attempt = 0;; attempt++) {
            try {
                return op.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt >= backoff.length) {
                    throw unreachable(e);
                }
                log.warn("Ranger transient error attempt {}/{}: {}", attempt + 1, backoff.length, e.toString());
                try {
                    Thread.sleep(backoff[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw unreachable(e);
                }
            }
        }
    }

    @Override
    public Optional<RangerPolicy> findPolicy(String serviceName, String policyName) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path(PATH_POLICY)
                .queryParam("serviceName", serviceName)
                .queryParam("policyName", policyName)
                .build().toUri();
        try {
            ResponseEntity<RangerPolicy> resp = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(authHeaders(config)), RangerPolicy.class);
            return Optional.ofNullable(resp.getBody())
                    .filter(p -> p.getId() != null || p.getName() != null);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unreachable(e);
        }
    }

    @Override
    public List<RangerPolicy> listPoliciesInService(String serviceName) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        // /v2/policy 对未知 serviceName 返空数组而非 404,需显式探测区分"无 policy"与"service 不存在"
        if (!Boolean.TRUE.equals(serviceExistsCache.get(serviceName, this::serviceExists))) {
            throw new BizException(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND, serviceName);
        }
        int pageSize = Math.max(1, orDefault(config.getRangerAdminPageSize(), 1_000));
        List<RangerPolicy> result = new ArrayList<>();
        int startIndex = 0;
        while (true) {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                    .path(PATH_POLICIES_BY_SERVICE)
                    .queryParam("serviceName", serviceName)
                    .queryParam("startIndex", startIndex)
                    .queryParam("pageSize", pageSize)
                    .build().toUri();
            ResponseEntity<RangerPolicy[]> resp;
            try {
                resp = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders(config)),
                        RangerPolicy[].class);
            } catch (HttpClientErrorException e) {
                throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
            } catch (HttpServerErrorException | ResourceAccessException e) {
                throw unreachable(e);
            }
            RangerPolicy[] page = resp.getBody();
            if (page == null || page.length == 0) {
                break;
            }
            for (RangerPolicy p : page) {
                result.add(p);
            }
            if (page.length < pageSize) {
                break; // 最后一页
            }
            startIndex += page.length;
        }
        return result;
    }

    @Override
    public void ensureUser(String username) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        if (!Boolean.TRUE.equals(config.getEnsureRangerUser())) {
            return; // 默认关:依赖外部 IDP/LDAP 同步用户到 Ranger
        }
        if (Boolean.TRUE.equals(userEnsuredCache.getIfPresent(username))) {
            return;
        }
        URI uri = URI.create(baseUrl(config) + PATH_USERS);
        // Ranger XUserMgr POST 必填 password + firstName + userRoleList;自建 user 只用于 policy 引用,
        // 不需要 user 实际登录 Ranger,给个满足强密码策略的固定值即可
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        body.put("password", "Rudder@DataPerm1");
        body.put("firstName", username);
        body.put("lastName", "");
        body.put("emailAddress", "");
        body.put("userRoleList", List.of("ROLE_USER"));
        body.put("groupIdList", List.of());
        body.put("userSource", 0);
        body.put("status", 1);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, jsonHeaders(config));
        try {
            restTemplate.exchange(uri, HttpMethod.POST, entity, Void.class);
            log.info("Ranger ensureUser ok: {}", username);
            userEnsuredCache.put(username, Boolean.TRUE);
        } catch (HttpClientErrorException.Conflict e) {
            // 409 = 用户名已存在,幂等成功
            log.debug("Ranger user already exists: {}", username);
            userEnsuredCache.put(username, Boolean.TRUE);
        } catch (HttpClientErrorException e) {
            // 其他 4xx (400 body 校验失败 / 403 凭证不足等) 不能当幂等;打 warn 让上层 reconciler 知道 createPolicy 会失败
            // 不入 cache,下轮再试
            log.warn("Ranger ensureUser failed: user={}, status={}, body={}",
                    username, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unreachable(e);
        }
    }

    @Override
    public RangerServiceDef getServiceDef(String serviceDefName) {
        DataPermConfigDTO config = requireEnabledAndConfigured();
        URI uri = UriComponentsBuilder.fromUriString(baseUrl(config))
                .path(PATH_SERVICEDEF_BY_NAME)
                .buildAndExpand(serviceDefName).toUri();
        try {
            ResponseEntity<RangerServiceDef> resp = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(authHeaders(config)), RangerServiceDef.class);
            return resp.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BizException(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND, e, serviceDefName);
        } catch (HttpClientErrorException e) {
            throw new BizException(DataPermErrorCode.RANGER_POLICY_CONFLICT, e, formatRangerError(e));
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw unreachable(e);
        }
    }

    // ---- helpers ----

    /** Ranger 返回的 HTTP 状态 + 响应体,用于回填 RANGER_POLICY_CONFLICT 错误码的 {0} 占位符;过长截断避免日志爆炸。 */
    private static String formatRangerError(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        String detail = body == null || body.isBlank() ? ""
                : ": " + (body.length() > 500 ? body.substring(0, 500) + "..." : body);
        return e.getStatusCode() + detail;
    }

    private DataPermConfigDTO requireEnabledAndConfigured() {
        DataPermConfigDTO config = configService.active();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BizException(DataPermErrorCode.APPLICATION_INVALID, "data perm disabled");
        }
        if (config.getRangerAdminUrl() == null || config.getRangerAdminUrl().isBlank()) {
            throw new BizException(DataPermErrorCode.RANGER_UNREACHABLE, "rangerAdminUrl not configured");
        }
        return config;
    }

    private static String baseUrl(DataPermConfigDTO config) {
        String url = config.getRangerAdminUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static HttpHeaders authHeaders(DataPermConfigDTO config) {
        HttpHeaders headers = new HttpHeaders();
        if (config.getRangerAdminUsername() != null && !config.getRangerAdminUsername().isBlank()) {
            headers.setBasicAuth(config.getRangerAdminUsername(),
                    config.getRangerAdminPassword() == null ? "" : config.getRangerAdminPassword());
        }
        return headers;
    }

    private static HttpHeaders jsonHeaders(DataPermConfigDTO config) {
        HttpHeaders headers = authHeaders(config);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static int orDefault(Integer v, int d) {
        return v == null ? d : v;
    }
}
