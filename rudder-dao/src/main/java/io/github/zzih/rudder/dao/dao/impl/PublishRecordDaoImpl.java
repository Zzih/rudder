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

import io.github.zzih.rudder.dao.dao.PublishRecordDao;
import io.github.zzih.rudder.dao.entity.PublishRecord;
import io.github.zzih.rudder.dao.mapper.PublishRecordMapper;
import io.github.zzih.rudder.dao.projection.PublishBatchDetailRow;
import io.github.zzih.rudder.dao.projection.PublishBatchRow;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PublishRecordDaoImpl implements PublishRecordDao {

    private final PublishRecordMapper publishRecordMapper;

    @Override
    public PublishRecord selectById(Long id) {
        return publishRecordMapper.selectById(id);
    }

    @Override
    public List<PublishRecord> selectByBatchCode(Long batchCode) {
        return publishRecordMapper.selectByBatchCode(batchCode);
    }

    @Override
    public int insert(PublishRecord record) {
        return publishRecordMapper.insert(record);
    }

    @Override
    public int updateById(PublishRecord record) {
        return publishRecordMapper.updateById(record);
    }

    @Override
    public IPage<PublishBatchRow> selectBatchPage(Long projectCode, String status,
                                                  int pageNum, int pageSize) {
        return publishRecordMapper.queryBatchPage(new Page<>(pageNum, pageSize), projectCode, status);
    }

    @Override
    public List<PublishBatchDetailRow> selectDetailsByBatchCodes(List<Long> batchCodes) {
        return publishRecordMapper.queryDetailsByBatchCodes(batchCodes);
    }
}
