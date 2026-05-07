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

package io.github.zzih.rudder.service.version;

import io.github.zzih.rudder.common.context.UserContext;
import io.github.zzih.rudder.common.enums.datatype.ResourceType;
import io.github.zzih.rudder.common.enums.error.WorkflowErrorCode;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.VersionRecordDao;
import io.github.zzih.rudder.service.config.VersionConfigService;
import io.github.zzih.rudder.version.api.VersionStore;
import io.github.zzih.rudder.version.api.model.DiffResult;
import io.github.zzih.rudder.version.api.model.VersionRecord;
import io.github.zzih.rudder.version.api.util.DagDiffComputer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.RequiredArgsConstructor;

/**
 * 版本管理对外入口。持有 {@link VersionRecordDao}(整个版本链路里唯一访问 dao 的地方),
 * 负责 versionNo 生成、索引读写、归属校验、diff;调 SPI {@link VersionStore} 处理内容存取。
 */
@Service
@RequiredArgsConstructor
public class VersionService {

    private final VersionRecordDao dao;
    private final VersionConfigService configService;

    // ==================== Save ====================

    /** 保存单文件版本(脚本场景)。返回的 record 含生成的 versionId 和 versionNo。 */
    public VersionRecord saveVersion(VersionRecord record) {
        return doSave(record, store -> store.save(record));
    }

    /** 保存多文件版本(工作流场景:dag.json + tasks/ + scripts/ 同 commit)。 */
    public VersionRecord saveMultiFileVersion(VersionRecord record, Map<String, String> files) {
        return doSave(record, store -> store.saveMultiFile(record, files));
    }

    private VersionRecord doSave(VersionRecord record, Function<VersionStore, String> saver) {
        VersionStore store = configService.required();
        String provider = configService.activeProvider();

        String storageRef = saver.apply(store);

        io.github.zzih.rudder.dao.entity.VersionRecord latest =
                dao.selectLatestByResource(record.getResourceType(), record.getResourceCode());
        int nextVersionNo = (latest == null) ? 1 : latest.getVersionNo() + 1;

        io.github.zzih.rudder.dao.entity.VersionRecord entity =
                new io.github.zzih.rudder.dao.entity.VersionRecord();
        entity.setResourceType(record.getResourceType());
        entity.setResourceCode(record.getResourceCode());
        entity.setVersionNo(nextVersionNo);
        entity.setStorageRef(storageRef);
        entity.setProvider(provider);
        entity.setRemark(record.getRemark());
        entity.setCreatedBy(UserContext.getUserId());
        entity.setCreatedAt(LocalDateTime.now());
        dao.insert(entity);
        return toModel(entity);
    }

    // ==================== Read ====================

    public VersionRecord get(Long versionId) {
        var entity = dao.selectById(versionId);
        return entity == null ? null : toModel(entity);
    }

    /** 取版本真实内容(SCRIPT 是脚本字符串,WORKFLOW 是 snapshot JSON)。 */
    public String getContent(Long versionId) {
        var entity = dao.selectById(versionId);
        return entity == null ? null : configService.required().load(entity.getStorageRef());
    }

    public List<VersionRecord> list(ResourceType resourceType, Long resourceCode) {
        return dao.selectByResourceOrderByVersionNoDesc(resourceType, resourceCode)
                .stream().map(this::toModel).toList();
    }

    public IPage<VersionRecord> page(ResourceType resourceType, Long resourceCode,
                                     int pageNum, int pageSize) {
        return dao.selectPageByResource(resourceType, resourceCode, pageNum, pageSize)
                .convert(this::toModel);
    }

    /** 校验版本归属。resourceType / resourceCode 不匹配则抛 NotFoundException。 */
    public VersionRecord getValidated(Long versionId, ResourceType expectedResourceType,
                                      Long expectedResourceCode) {
        var entity = requireOwnedEntity(versionId, expectedResourceType, expectedResourceCode);
        return toModel(entity);
    }

    /**
     * 一站式取版本元数据 + 内容。一次 dao 命中 + 一次 SPI load,避免调用方先 getValidated 再 getContent
     * 重复 selectById。
     */
    public VersionRecord getValidatedWithContent(Long versionId, ResourceType expectedResourceType,
                                                 Long expectedResourceCode) {
        var entity = requireOwnedEntity(versionId, expectedResourceType, expectedResourceCode);
        VersionRecord record = toModel(entity);
        record.setContent(configService.required().load(entity.getStorageRef()));
        return record;
    }

    // ==================== Diff ====================

    /** 一次 dao 各拉一行 + 各调一次 SPI load。 */
    public DiffResult diff(Long versionIdA, Long versionIdB) {
        var entityA = dao.selectById(versionIdA);
        var entityB = dao.selectById(versionIdB);
        if (entityA == null || entityB == null) {
            return null;
        }
        VersionStore store = configService.required();
        return DagDiffComputer.buildDiff(versionIdA, versionIdB,
                store.load(entityA.getStorageRef()),
                store.load(entityB.getStorageRef()),
                entityA.getResourceType());
    }

    // ==================== Internal ====================

    private io.github.zzih.rudder.dao.entity.VersionRecord requireOwnedEntity(
                                                                              Long versionId,
                                                                              ResourceType expectedResourceType,
                                                                              Long expectedResourceCode) {
        var entity = dao.selectById(versionId);
        if (entity == null
                || expectedResourceType != entity.getResourceType()
                || !expectedResourceCode.equals(entity.getResourceCode())) {
            throw new NotFoundException(WorkflowErrorCode.VERSION_NOT_FOUND);
        }
        return entity;
    }

    /** entity → SPI model。content 字段不填(getContent / getValidatedWithContent 才走 SPI load)。 */
    private VersionRecord toModel(io.github.zzih.rudder.dao.entity.VersionRecord entity) {
        return BeanConvertUtils.convert(entity, VersionRecord.class);
    }
}
