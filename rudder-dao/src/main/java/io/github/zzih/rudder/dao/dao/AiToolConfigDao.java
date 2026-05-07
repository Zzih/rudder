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

import io.github.zzih.rudder.dao.entity.AiToolConfig;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface AiToolConfigDao {

    /** 按工具名查唯一配置行(tool_name 有 UNIQUE 约束)。 */
    AiToolConfig selectByToolName(String toolName);

    List<AiToolConfig> selectAll();

    IPage<AiToolConfig> selectPage(int pageNum, int pageSize);

    int insert(AiToolConfig entity);

    int updateById(AiToolConfig entity);

    int deleteById(Long id);
}
