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

package io.github.zzih.rudder.service.dataperm.notification;

import io.github.zzih.rudder.common.i18n.I18n;
import io.github.zzih.rudder.notification.api.model.NotificationEventType;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.PlainMessage;

/**
 * 数据权限模块的通知模板工厂。
 *
 * <p>文案走 `i18n/dataperm_{zh,en}.properties` bundle 解析,业务侧不写字面量。
 * 落地形式仍为 {@link PlainMessage} —— 不引入新的 sealed 子类型,避免破坏既有 sender switch。
 */
public final class DataPermNotifications {

    private DataPermNotifications() {
    }

    /** Reconciler 连续多轮整体失败(Ranger Admin 不可达)。 */
    public static PlainMessage syncFailure(int consecutiveFailures, String lastError) {
        return PlainMessage.builder()
                .level(NotificationLevel.ERROR)
                .eventType(NotificationEventType.DATA_PERM_RECONCILE_RANGER_DOWN)
                .title(I18n.t("dataperm.notification.syncFailure.title"))
                .content(I18n.t("dataperm.notification.syncFailure.content",
                        consecutiveFailures, lastError == null ? "" : lastError))
                .build();
    }

    /** 申请审批通过但业务侧 onFinalized 重试 3 次仍失败。 */
    public static PlainMessage approvalIntegrationFailure(long approvalId, String lastError) {
        return PlainMessage.builder()
                .level(NotificationLevel.FAIL)
                .eventType(NotificationEventType.APPROVAL)
                .title(I18n.t("dataperm.notification.approvalIntegrationFailure.title"))
                .content(I18n.t("dataperm.notification.approvalIntegrationFailure.content",
                        approvalId, lastError == null ? "" : lastError))
                .build();
    }
}
