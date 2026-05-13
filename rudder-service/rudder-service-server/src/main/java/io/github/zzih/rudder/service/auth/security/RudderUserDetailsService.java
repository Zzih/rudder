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

import io.github.zzih.rudder.common.enums.auth.RoleType;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 本地账号(t_r_user) → {@link UserDetails}。密码空 / 用户不存在统一抛
 * {@link UsernameNotFoundException} 防 user enumeration。
 */
@Service
@RequiredArgsConstructor
public class RudderUserDetailsService implements UserDetailsService {

    private final UserDao userDao;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userDao.selectByUsername(username);
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new UsernameNotFoundException("user not found");
        }
        Collection<GrantedAuthority> authorities = Boolean.TRUE.equals(user.getIsSuperAdmin())
                ? RudderAuthorities.from(RoleType.SUPER_ADMIN.name())
                : RudderAuthorities.from(null);
        return new RudderUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getIsSuperAdmin()),
                authorities);
    }
}
