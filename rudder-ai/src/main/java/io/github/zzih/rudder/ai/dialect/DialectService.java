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

package io.github.zzih.rudder.ai.dialect;

import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.common.utils.io.ClasspathResourceUtils;
import io.github.zzih.rudder.dao.dao.AiDialectDao;
import io.github.zzih.rudder.dao.entity.AiDialect;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheKey;
import io.github.zzih.rudder.service.coordination.cache.GlobalCacheService;
import io.github.zzih.rudder.task.api.task.enums.TaskCategory;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 方言 prompt 解析器。
 * <p>
 * 优先级：DB 中 enabled 的覆盖行 → classpath {@code ai-prompts/dialects/{taskType}.md} 出厂默认。
 * classpath 内容缓存后基本零成本；DB 覆盖通过 {@link GlobalCacheService} 跨节点 pub/sub 同步，
 * admin 改完所有节点立即生效。
 */
@Service
@RequiredArgsConstructor
public class DialectService {

    private final AiDialectDao dao;
    private final GlobalCacheService cache;

    private final Map<String, String> classpathCache = new ConcurrentHashMap<>();

    /** 按 TaskType 分类决定是否需要方言指导(SQL / SCRIPT / DATA_INTEGRATION)。 */
    public static boolean needsDialect(TaskType tt) {
        if (tt == null) {
            return false;
        }
        TaskCategory c = tt.getCategory();
        return c == TaskCategory.SQL || c == TaskCategory.SCRIPT || c == TaskCategory.DATA_INTEGRATION;
    }

    /** 列出所有"需要方言指导"的 TaskType —— Admin UI 渲染槽位列表用。 */
    public List<TaskType> listSlots() {
        List<TaskType> out = new ArrayList<>();
        for (TaskType tt : TaskType.values()) {
            if (needsDialect(tt)) {
                out.add(tt);
            }
        }
        return out;
    }

    /** 读一个 TaskType 的方言 prompt 正文。找不到返回空串。 */
    public String loadContent(TaskType tt) {
        if (!needsDialect(tt)) {
            return "";
        }
        String override = currentDbCache().get(tt.name());
        if (override != null) {
            return override;
        }
        return loadClasspathDefault(tt);
    }

    /** classpath 出厂默认（给 UI "重置"按钮预览用）。 */
    public String loadClasspathDefault(TaskType tt) {
        if (!needsDialect(tt)) {
            return "";
        }
        return classpathCache.computeIfAbsent(tt.name(),
                k -> ClasspathResourceUtils.readTextOrEmpty("ai-prompts/dialects/" + k + ".md"));
    }

    /** 当前 DB 里是否有此 TaskType 的 enabled 覆盖。 */
    public boolean hasOverride(TaskType tt) {
        return currentDbCache().containsKey(tt.name());
    }

    /** upsert 覆盖。 */
    public void upsertOverride(String taskTypeName, String content, boolean enabled) {
        TaskType tt = parse(taskTypeName);
        if (tt == null || !needsDialect(tt)) {
            throw new IllegalArgumentException(I18n.t(
                    "err.dialect.taskType.unsupported", taskTypeName));
        }
        AiDialect existing = dao.selectByTaskType(taskTypeName);
        if (existing == null) {
            AiDialect entity = new AiDialect();
            entity.setTaskType(taskTypeName);
            entity.setContent(content == null ? "" : content);
            entity.setEnabled(enabled);
            dao.insert(entity);
        } else {
            existing.setContent(content == null ? "" : content);
            existing.setEnabled(enabled);
            dao.updateById(existing);
        }
        cache.invalidate(GlobalCacheKey.DIALECT);
    }

    /** 删除 DB 覆盖行，回到 classpath 出厂默认。 */
    public void resetToDefault(String taskTypeName) {
        dao.deleteByTaskType(taskTypeName);
        cache.invalidate(GlobalCacheKey.DIALECT);
    }

    private Map<String, String> currentDbCache() {
        return cache.getOrLoad(GlobalCacheKey.DIALECT, this::loadFromDb);
    }

    private Map<String, String> loadFromDb() {
        Map<String, String> fresh = new HashMap<>();
        for (AiDialect row : dao.selectAll()) {
            if (!Boolean.FALSE.equals(row.getEnabled()) && row.getContent() != null) {
                fresh.put(row.getTaskType(), row.getContent());
            }
        }
        return fresh;
    }

    private static TaskType parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return TaskType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
