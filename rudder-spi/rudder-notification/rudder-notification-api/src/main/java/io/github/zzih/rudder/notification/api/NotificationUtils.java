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

package io.github.zzih.rudder.notification.api;

import io.github.zzih.rudder.notification.api.model.NodeInfo;
import io.github.zzih.rudder.notification.api.model.NotificationLevel;
import io.github.zzih.rudder.notification.api.model.UserRef;

import java.util.List;

public final class NotificationUtils {

    private NotificationUtils() {
    }

    public static String defaultStr(String s) {
        return s != null ? s : "-";
    }

    public static String levelEmoji(NotificationLevel level) {
        if (level == null) {
            return "ℹ️";
        }
        return switch (level) {
            case SUCCESS -> "✅";
            case WARN -> "⚠️";
            case FAIL, ERROR -> "❌";
        };
    }

    public static String userDisplay(UserRef u) {
        return u == null ? "-" : defaultStr(u.username());
    }

    public static String userListDisplay(List<UserRef> users) {
        if (users == null || users.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(defaultStr(users.get(i).username()));
        }
        return sb.toString();
    }

    public static List<UserRef> singletonOrEmpty(UserRef u) {
        return u == null ? List.of() : List.of(u);
    }

    /**
     * 渲染节点列表为 markdown 文本（state 行 + 每节点一行）。
     * bullet 控制每行的项目符号（"-" / "•"），各 channel 自行选；列表为空时返回 emptyPlaceholder。
     */
    public static String formatNodeList(List<NodeInfo> nodes, String stateLabel, String bullet) {
        StringBuilder body = new StringBuilder("**State**: ").append(stateLabel).append("\n\n");
        if (nodes.isEmpty()) {
            body.append(bullet).append(" (no node info)");
            return body.toString();
        }
        for (NodeInfo n : nodes) {
            body.append(bullet).append(" `").append(n.displayLabel()).append("`\n");
        }
        return body.toString().stripTrailing();
    }
}
