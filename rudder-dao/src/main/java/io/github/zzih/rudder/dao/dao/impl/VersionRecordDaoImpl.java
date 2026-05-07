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

import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.dao.dao.VersionRecordDao;
import io.github.zzih.rudder.dao.entity.VersionRecord;
import io.github.zzih.rudder.dao.mapper.VersionRecordMapper;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class VersionRecordDaoImpl implements VersionRecordDao {

    private final VersionRecordMapper versionRecordMapper;

    @Override
    public VersionRecord selectById(Long versionId) {
        return versionRecordMapper.selectById(versionId);
    }

    @Override
    public List<VersionRecord> selectByResourceOrderByVersionNoDesc(ResourceType resourceType,
                                                                    Long resourceCode) {
        return versionRecordMapper.queryByResourceOrderByVersionNoDesc(resourceType, resourceCode);
    }

    @Override
    public IPage<VersionRecord> selectPageByResource(ResourceType resourceType, Long resourceCode, int pageNum,
                                                     int pageSize) {
        return versionRecordMapper.queryPageByResource(new Page<>(pageNum, pageSize), resourceType, resourceCode);
    }

    @Override
    public VersionRecord selectLatestByResource(ResourceType resourceType, Long resourceCode) {
        return versionRecordMapper.queryLatestByResource(resourceType, resourceCode);
    }

    @Override
    public int insert(VersionRecord entity) {
        return versionRecordMapper.insert(entity);
    }
}
