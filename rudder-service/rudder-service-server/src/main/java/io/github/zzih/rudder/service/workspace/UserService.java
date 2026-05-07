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

package io.github.zzih.rudder.service.workspace;

import io.github.zzih.rudder.common.enums.error.WorkspaceErrorCode;
import io.github.zzih.rudder.common.exception.AuthException;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.UserDao;
import io.github.zzih.rudder.dao.entity.User;
import io.github.zzih.rudder.service.workspace.dto.UserDTO;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User create(String username, String password, String email) {
        log.info("创建用户, username={}, email={}", username, email);
        if (userDao.countByUsername(username) > 0) {
            log.warn("创建用户失败: 用户名已存在, username={}", username);
            throw new BizException(WorkspaceErrorCode.USER_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        userDao.insert(user);
        log.info("用户创建成功, username={}, userId={}", username, user.getId());
        return user;
    }

    public User getById(Long id) {
        User user = userDao.selectById(id);
        if (user == null) {
            throw new NotFoundException(WorkspaceErrorCode.USER_NOT_FOUND, id);
        }
        return user;
    }

    public User getByUsername(String username) {
        return userDao.selectByUsername(username);
    }

    public IPage<User> page(String searchVal, int pageNum, int pageSize) {
        return userDao.selectPage(searchVal, pageNum, pageSize);
    }

    public void updatePassword(Long id, String oldPassword, String newPassword) {
        log.info("用户修改密码, userId={}", id);
        User user = getById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("修改密码失败: 旧密码错误, userId={}", id);
            throw new AuthException(WorkspaceErrorCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updateById(user);
        log.info("用户密码修改成功, userId={}", id);
    }

    public void resetPassword(Long id, String newPassword) {
        log.info("重置用户密码, userId={}", id);
        User user = getById(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userDao.updateById(user);
    }

    public void updateSuperAdmin(Long id, boolean isSuperAdmin) {
        log.info("变更超级管理员权限, userId={}, isSuperAdmin={}", id, isSuperAdmin);
        User user = getById(id);
        user.setIsSuperAdmin(isSuperAdmin);
        userDao.updateById(user);
    }

    public void updateEmail(Long id, String email) {
        User user = getById(id);
        user.setEmail(email);
        userDao.updateById(user);
    }

    public List<User> listAll() {
        return userDao.selectAll();
    }

    public void delete(Long id) {
        log.info("删除用户, userId={}", id);
        getById(id);
        userDao.deleteById(id);
    }

    // ===== Detail variants — controller 调,返 DTO 不返 entity =====

    public UserDTO createDetail(String username, String password, String email) {
        return BeanConvertUtils.convert(create(username, password, email), UserDTO.class);
    }

    public UserDTO getByIdDetail(Long id) {
        return BeanConvertUtils.convert(getById(id), UserDTO.class);
    }

    public IPage<UserDTO> pageDetail(String searchVal, int pageNum, int pageSize) {
        return BeanConvertUtils.convertPage(page(searchVal, pageNum, pageSize), UserDTO.class);
    }

    public List<UserDTO> listAllDetail() {
        return BeanConvertUtils.convertList(listAll(), UserDTO.class);
    }
}
