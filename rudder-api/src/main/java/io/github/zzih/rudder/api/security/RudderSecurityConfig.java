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

import io.github.zzih.rudder.api.security.oidc.DbClientRegistrationRepository;
import io.github.zzih.rudder.api.security.oidc.JwtIssuanceSuccessHandler;
import io.github.zzih.rudder.api.security.oidc.RedisOAuth2AuthorizationRequestRepository;
import io.github.zzih.rudder.api.security.oidc.RudderOidcUserService;
import io.github.zzih.rudder.mcp.auth.McpTokenService;
import io.github.zzih.rudder.mcp.auth.PatAuthFilter;
import io.github.zzih.rudder.service.auth.security.RudderUserDetailsService;
import io.github.zzih.rudder.service.workspace.MemberService;
import io.github.zzih.rudder.service.workspace.UserService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.client.RestClient;

/**
 * 三条 SecurityFilterChain,按 @Order 路径排他:{@code /mcp/**} 由 {@link PatAuthFilter} 用 PAT 鉴权;
 * OIDC 回调路径走 oauth2Login;其他路径走 oauth2-resource-server 校验 JWT。三链均 stateless,授权由
 * controller 方法注解负责。
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

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oidcTokenResponseClient() {
        RestClient restClient = RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory()))
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
    public JwtAuthFilter jwtAuthFilter(MemberService memberService) {
        return new JwtAuthFilter(memberService);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
    public PatAuthFilter patAuthFilter(McpTokenService tokenService,
                                       UserService userService,
                                       MemberService memberService) {
        return new PatAuthFilter(tokenService, userService, memberService);
    }

    /**
     * 关闭 Spring Boot 对自定义 Filter bean 的全局 servlet 自动注册。两个 filter 都只在各自
     * SecurityFilterChain 里跑(addFilterBefore / addFilterAfter),否则 Boot 会再把它们作为
     * 全局 filter 注册一份,**对所有路径生效**,例如 PatAuthFilter 会把 {@code /api/auth/login}
     * 也拦下 401。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterDisableAutoRegistration(JwtAuthFilter f) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
    public FilterRegistrationBean<PatAuthFilter> patAuthFilterDisableAutoRegistration(PatAuthFilter f) {
        FilterRegistrationBean<PatAuthFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    /** /mcp/** 不挂 oauth2ResourceServer,避免把 PAT 当 JWT 解析。挂 jsonAuthenticationEntryPoint 保证未鉴权请求返 JSON 而非 HTML 错误页。 */
    @Bean
    @Order(0)
    @ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
    public SecurityFilterChain mcpFilterChain(HttpSecurity http,
                                              PatAuthFilter patAuthFilter,
                                              JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                                              RudderAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher("/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(patAuthFilter, AuthorizationFilter.class)
                .build();
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
                                               JwtAuthFilter jwtAuthFilter,
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
                        // /mcp/** 兜底:mcpFilterChain @Order(0) 通常先抢匹配;mcp.server.enabled=false
                        // 时该链不存在,流量落到这里,permitAll 让 DispatcherServlet 返 404 而非 401。
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
                .addFilterAfter(jwtAuthFilter, BasicAuthenticationFilter.class)
                .build();
    }
}
