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

package io.github.zzih.rudder.dao.dao;

import io.github.zzih.rudder.dao.entity.User;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface UserDao {

    User selectById(Long id);

    List<User> selectByIds(Collection<Long> ids);

    User selectByUsername(String username);

    User selectByEmail(String email);

    User selectBySso(String ssoProvider, String ssoId);

    long countByUsername(String username);

    IPage<User> selectPage(String searchVal, int pageNum, int pageSize);

    int insert(User user);

    int updateById(User user);

    int deleteById(Long id);

    List<User> selectAll();

    /** 全部 SUPER_ADMIN 用户。MCP 高敏 capability 二级审批解析候选人用。 */
    List<User> selectSuperAdmins();
}
