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

package io.github.zzih.rudder.service.quicklink;

import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.enums.quicklink.QuickLinkCategory;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;
import io.github.zzih.rudder.dao.dao.QuickLinkDao;
import io.github.zzih.rudder.dao.entity.QuickLink;
import io.github.zzih.rudder.service.quicklink.dto.QuickLinkDTO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 首页快捷入口管理。读公开（已登录），写仅 SUPER_ADMIN。
 *
 * <p>icon 存 base64 data URL（{@code data:image/svg+xml;base64,...}），
 * 解码后大小 ≤ 64KB,且必须是 SVG —— 防止滥用为 binary 仓库。
 */
@Service
@RequiredArgsConstructor
public class QuickLinkService {

    private static final String ICON_PREFIX = "data:image/svg+xml;base64,";
    private static final int ICON_MAX_BYTES = 64 * 1024;
    private static final Set<String> ALLOWED_TARGETS = Set.of("_blank", "_self");

    private final QuickLinkDao quickLinkDao;

    public List<QuickLinkDTO> list(QuickLinkCategory category, Boolean onlyEnabled) {
        return BeanConvertUtils.convertList(quickLinkDao.selectList(category, onlyEnabled), QuickLinkDTO.class);
    }

    public QuickLinkDTO create(QuickLinkDTO body) {
        validate(body);
        QuickLink entity = BeanConvertUtils.convert(body, QuickLink.class);
        entity.setId(null);
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getTarget() == null) {
            entity.setTarget("_blank");
        }
        quickLinkDao.insert(entity);
        return BeanConvertUtils.convert(entity, QuickLinkDTO.class);
    }

    public QuickLinkDTO update(Long id, QuickLinkDTO body) {
        validate(body);
        QuickLink entity = BeanConvertUtils.convert(body, QuickLink.class);
        entity.setId(id);
        if (quickLinkDao.updateById(entity) == 0) {
            throw new BizException(SystemErrorCode.NOT_FOUND);
        }
        return BeanConvertUtils.convert(quickLinkDao.selectById(id), QuickLinkDTO.class);
    }

    public void delete(Long id) {
        quickLinkDao.deleteById(id);
    }

    @Transactional
    public void updateSort(List<Long> idsInOrder) {
        if (idsInOrder == null || idsInOrder.isEmpty()) {
            return;
        }
        for (int i = 0; i < idsInOrder.size(); i++) {
            QuickLink patch = new QuickLink();
            patch.setId(idsInOrder.get(i));
            patch.setSortOrder(i);
            quickLinkDao.updateById(patch);
        }
    }

    private void validate(QuickLinkDTO body) {
        // category/name/url 已由 controller 层 @Valid + @NotNull/@NotBlank 守护;此处只兜底业务规则。
        if (body.getTarget() != null && !ALLOWED_TARGETS.contains(body.getTarget())) {
            throw new BizException(SystemErrorCode.BAD_REQUEST);
        }
        validateIcon(body.getIcon());
    }

    private void validateIcon(String icon) {
        if (icon == null || icon.isEmpty()) {
            return;
        }
        if (!icon.startsWith(ICON_PREFIX)) {
            throw new BizException(SystemErrorCode.BAD_REQUEST);
        }
        String base64 = icon.substring(ICON_PREFIX.length());
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BizException(SystemErrorCode.BAD_REQUEST, e);
        }
        if (decoded.length > ICON_MAX_BYTES) {
            throw new BizException(SystemErrorCode.BAD_REQUEST);
        }
        // sniff: SVG 必须包含 <svg；防止把 PNG 等 binary 塞进来
        String sniff = new String(decoded, 0, Math.min(decoded.length, 512), StandardCharsets.UTF_8);
        if (!sniff.contains("<svg")) {
            throw new BizException(SystemErrorCode.BAD_REQUEST);
        }
    }
}
