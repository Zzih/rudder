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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.zzih.rudder.common.enums.error.DataPermErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.dao.dao.DataPermPlatformConfigDao;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.service.dataperm.config.DataPermConfigService;
import io.github.zzih.rudder.service.dataperm.dto.DataPermConfigDTO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * {@link RangerAdminRestClientImpl} 单测:用 {@link MockRestServiceServer} mock Ranger HTTP 端点。
 *
 * <p>覆盖维度:成功路径 + 404 / 4xx / 5xx 错误码映射 + 分页 + 禁用 / 未配 URL 防御。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RangerAdminRestClientImplTest {

    private static final String ADMIN_URL = "http://ranger.test:6080";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    @Mock
    private GlobalCacheService cache;
    @Mock
    private DataPermPlatformConfigDao configDao;
    private DataPermConfigService configService;
    private final AtomicReference<DataPermConfigDTO> currentConfig = new AtomicReference<>();
    private RangerAdminRestClientImpl client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        currentConfig.set(enabledConfig(1_000));
        // cache.getOrLoad 不走 supplier,直接返回当前 currentConfig — 允许 test 通过 currentConfig.set 动态切换
        when(cache.getOrLoad(eq(GlobalCacheKey.DATA_PERM), any())).thenAnswer(inv -> currentConfig.get());
        configService = new DataPermConfigService(cache, configDao, null, null, null, null, null);
        client = new RangerAdminRestClientImpl(configService, restTemplate);
    }

    private static DataPermConfigDTO enabledConfig(int pageSize) {
        return scalarDto(true, ADMIN_URL, "admin", "pwd", pageSize);
    }

    private static DataPermConfigDTO scalarDto(boolean enabled, String url, String user, String pw, int pageSize) {
        DataPermConfigDTO d = new DataPermConfigDTO();
        d.setEnabled(enabled);
        d.setRangerModeEnabled(true);
        d.setLocalModeEnabled(true);
        d.setRangerAdminUrl(url);
        d.setRangerAdminUsername(user);
        d.setRangerAdminPassword(pw);
        d.setRangerAdminTimeoutMs(10_000);
        d.setRangerAdminPageSize(pageSize);
        d.setRangerWriteConcurrency(4);
        d.setReconcileIntervalSeconds(300);
        d.setReconcileLockTtlSeconds(600);
        d.setReconcileBatchSize(100);
        d.setReconcileFailureAlertThreshold(3);
        d.setEnsureRangerUser(false);
        return d;
    }

    private static DataPermConfigDTO disabledDefault() {
        DataPermConfigDTO d = new DataPermConfigDTO();
        d.setEnabled(false);
        d.setRangerModeEnabled(false);
        d.setLocalModeEnabled(false);
        return d;
    }

    // ---------- 防御 ----------

    @Test
    void disabledConfig_throwsBizException() {
        currentConfig.set(disabledDefault());
        assertThatThrownBy(() -> client.serviceExists("hive_prod"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.APPLICATION_INVALID);
    }

    @Test
    void emptyUrl_throwsBizException() {
        currentConfig.set(scalarDto(true, "  ", "u", "p", 1000));
        assertThatThrownBy(() -> client.serviceExists("hive_prod"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(DataPermErrorCode.RANGER_UNREACHABLE);
    }

    // ---------- serviceExists ----------

    @Test
    void serviceExists_200_returnsTrue() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/hive_prod"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.serviceExists("hive_prod")).isTrue();
        server.verify();
    }

    @Test
    void serviceExists_404_returnsFalse() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/no_such"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.serviceExists("no_such")).isFalse();
        server.verify();
    }

    @Test
    void serviceExists_500_throwsUnreachable() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/hive_prod"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.serviceExists("hive_prod"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(DataPermErrorCode.RANGER_UNREACHABLE);
        server.verify();
    }

    // ---------- createPolicy ----------

    @Test
    void createPolicy_200_returnsPolicyWithId() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":42,\"name\":\"rudder-alice-ds1-p\"}",
                        MediaType.APPLICATION_JSON));

        RangerPolicy req = RangerPolicy.builder().name("rudder-alice-ds1-p").service("hive_prod").build();
        RangerPolicy resp = client.createPolicy(req);
        assertThat(resp.getId()).isEqualTo(42L);
        server.verify();
    }

    @Test
    void createPolicy_400_throwsConflict() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.createPolicy(RangerPolicy.builder().build()))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(DataPermErrorCode.RANGER_POLICY_CONFLICT);
        server.verify();
    }

    // ---------- updatePolicy ----------

    @Test
    void updatePolicy_200_returnsBody() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy/42"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{\"id\":42,\"name\":\"x\"}", MediaType.APPLICATION_JSON));

        RangerPolicy resp = client.updatePolicy(42L, RangerPolicy.builder().build());
        assertThat(resp.getId()).isEqualTo(42L);
        server.verify();
    }

    // ---------- deletePolicy ----------

    @Test
    void deletePolicy_204_succeeds() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy/7"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deletePolicy(7L);
        server.verify();
    }

    @Test
    void deletePolicy_404_silentlySucceeds() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy/7"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        client.deletePolicy(7L); // 不抛
        server.verify();
    }

    // ---------- findPolicy ----------

    @Test
    void findPolicy_200_withBody_returnsPresent() {
        server.expect(
                requestTo(ADMIN_URL + "/service/public/v2/api/policy?serviceName=hive_prod&policyName=rudder-alice"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":11,\"name\":\"rudder-alice\"}",
                        MediaType.APPLICATION_JSON));

        Optional<RangerPolicy> result = client.findPolicy("hive_prod", "rudder-alice");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(11L);
        server.verify();
    }

    @Test
    void findPolicy_404_returnsEmpty() {
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/policy?serviceName=hive_prod&policyName=missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findPolicy("hive_prod", "missing")).isEmpty();
        server.verify();
    }

    // ---------- listPoliciesInService 分页 ----------

    @Test
    void listPolicies_singlePage_returnsAll() {
        currentConfig.set(enabledConfig(10)); // pageSize=10

        // serviceExistsCache probe
        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/hive_prod"))
                .andRespond(withSuccess("{\"name\":\"hive_prod\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_URL
                + "/service/public/v2/api/policy?serviceName=hive_prod&startIndex=0&pageSize=10"))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"name\":\"p1\"},{\"id\":2,\"name\":\"p2\"}]",
                        MediaType.APPLICATION_JSON));

        List<RangerPolicy> all = client.listPoliciesInService("hive_prod");
        assertThat(all).hasSize(2);
        assertThat(all).extracting(RangerPolicy::getId).containsExactly(1L, 2L);
        server.verify();
    }

    @Test
    void listPolicies_multiPage_concatenates() {
        currentConfig.set(enabledConfig(2)); // pageSize=2

        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/hive_prod"))
                .andRespond(withSuccess("{\"name\":\"hive_prod\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_URL
                + "/service/public/v2/api/policy?serviceName=hive_prod&startIndex=0&pageSize=2"))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"name\":\"p1\"},{\"id\":2,\"name\":\"p2\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_URL
                + "/service/public/v2/api/policy?serviceName=hive_prod&startIndex=2&pageSize=2"))
                .andRespond(withSuccess(
                        "[{\"id\":3,\"name\":\"p3\"}]",
                        MediaType.APPLICATION_JSON));

        List<RangerPolicy> all = client.listPoliciesInService("hive_prod");
        assertThat(all).hasSize(3);
        assertThat(all).extracting(RangerPolicy::getId).containsExactly(1L, 2L, 3L);
        server.verify();
    }

    @Test
    void listPolicies_serviceNotFound_throwsServiceNotFound() {
        currentConfig.set(enabledConfig(10));

        server.expect(requestTo(ADMIN_URL + "/service/public/v2/api/service/name/no_such"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.listPoliciesInService("no_such"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(DataPermErrorCode.RANGER_SERVICE_NOT_FOUND);
        server.verify();
    }

    // ---------- ensureUser ----------

    @Test
    void ensureUser_disabledByDefault_noHttpCall() {
        client.ensureUser("alice"); // ensureRangerUser=false → 应当无 HTTP 调用
        server.verify();
    }

    @Test
    void ensureUser_enabledAndCreated() {
        DataPermConfigDTO ensure = scalarDto(true, ADMIN_URL, "admin", "pwd", 1000);
        ensure.setEnsureRangerUser(true);
        currentConfig.set(ensure);
        server.expect(requestTo(ADMIN_URL + "/service/xusers/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.ensureUser("alice");
        server.verify();
    }

    @Test
    void ensureUser_alreadyExists_4xx_treatedAsIdempotent() {
        DataPermConfigDTO ensure = scalarDto(true, ADMIN_URL, "admin", "pwd", 1000);
        ensure.setEnsureRangerUser(true);
        currentConfig.set(ensure);
        server.expect(requestTo(ADMIN_URL + "/service/xusers/users"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        client.ensureUser("alice"); // 不抛
        server.verify();
    }
}
