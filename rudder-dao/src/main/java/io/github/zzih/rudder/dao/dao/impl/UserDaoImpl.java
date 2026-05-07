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

package io.github.zzih.rudder.dao.dao.impl;

import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.dao.mapper.UserMapper;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserDaoImpl implements UserDao {

    private final UserMapper userMapper;

    @Override
    public User selectById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public List<User> selectByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids);
    }

    @Override
    public User selectByUsername(String username) {
        return userMapper.queryByUsername(username);
    }

    @Override
    public User selectByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userMapper.queryByEmail(email);
    }

    @Override
    public User selectBySso(String ssoProvider, String ssoId) {
        return userMapper.queryBySso(ssoProvider, ssoId);
    }

    @Override
    public long countByUsername(String username) {
        return userMapper.countByUsername(username);
    }

    @Override
    public IPage<User> selectPage(String searchVal, int pageNum, int pageSize) {
        return userMapper.queryPage(new Page<>(pageNum, pageSize), searchVal);
    }

    @Override
    public int insert(User user) {
        return userMapper.insert(user);
    }

    @Override
    public int updateById(User user) {
        return userMapper.updateById(user);
    }

    @Override
    public int deleteById(Long id) {
        return userMapper.deleteById(id);
    }

    @Override
    public List<User> selectAll() {
        return userMapper.queryAll();
    }

    @Override
    public List<User> selectSuperAdmins() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getIsSuperAdmin, Boolean.TRUE));
    }
}
