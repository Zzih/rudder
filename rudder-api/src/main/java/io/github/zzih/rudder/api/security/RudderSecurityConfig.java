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

package io.github.zzih.rudder.api.security;

import io.github.zzih.rudder.service.auth.security.RudderUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.client.RestClient;

/**
 * 双 SecurityFilterChain:OIDC 回调路径走 oauth2Login;其他路径走 oauth2-resource-server
 * 校验 Bearer JWT,授权由 controller 方法注解负责。两链均 stateless。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class RudderSecurityConfig {

    /** SUPER_ADMIN > WORKSPACE_OWNER > DEVELOPER > VIEWER,让 {@code hasRole('VIEWER')} 自动覆盖上层。 */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_WORKSPACE_OWNER
                ROLE_WORKSPACE_OWNER > ROLE_DEVELOPER
                ROLE_DEVELOPER > ROLE_VIEWER
                """);
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        resolver.setAllowUriQueryParameter(true);
        return resolver;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * PASSWORD 登录用的 AuthenticationManager。
     * 持 {@link DaoAuthenticationProvider}({@link RudderUserDetailsService} + BCrypt),
     * LDAP / OIDC 走自己的 chain(不进 ProviderManager)。
     */
    @Bean
    public AuthenticationManager authenticationManager(RudderUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * 显式注册 OAuth2 专用 converter,避免回退到 Jackson 默认 converter 反序列化
     * {@link org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse}
     * (immutable + builder,无默认构造器) 触发 {@code invalid_token_response}。
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oidcTokenResponseClient() {
        RestClient restClient = RestClient.builder()
                .configureMessageConverters(c -> c
                        .disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        RestClientAuthorizationCodeTokenResponseClient client =
                new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        return client;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(
                                                      HttpSecurity http,
                                                      DbClientRegistrationRepository clientRegistrationRepository,
                                                      RudderOidcUserService rudderOidcUserService,
                                                      JwtIssuanceSuccessHandler jwtIssuanceSuccessHandler,
                                                      RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository,
                                                      OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oidcTokenResponseClient) throws Exception {
        return http
                .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2Login(login -> login
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .authorizationEndpoint(ae -> ae.authorizationRequestRepository(authorizationRequestRepository))
                        .tokenEndpoint(t -> t.accessTokenResponseClient(oidcTokenResponseClient))
                        .userInfoEndpoint(u -> u.oidcUserService(rudderOidcUserService))
                        .successHandler(jwtIssuanceSuccessHandler))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain mainFilterChain(HttpSecurity http,
                                               JwtToUserContextFilter jwtToUserContextFilter,
                                               JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                               RudderAccessDeniedHandler accessDeniedHandler,
                                               BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 前端 SPA 静态资源(WebMvcConfig 把 /ui/** + / 转发到 ui/index.html)
                        .requestMatchers("/", "/ui/**", "/favicon.ico", "/error").permitAll()
                        // 登录前置端点(无 JWT)
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/sources",
                                "/api/auth/sources/*/login")
                        .permitAll()
                        // 外部审批系统回调(由 notifier 校验签名)
                        .requestMatchers("/api/approvals/callback/**").permitAll()
                        // MCP 协议入口:由 PatAuthFilter 用 PAT 鉴权,跳过 Spring Security
                        .requestMatchers("/mcp/**").permitAll()
                        // 健康检查
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(eh -> eh.accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(jwtToUserContextFilter, BasicAuthenticationFilter.class)
                .build();
    }
}
