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

package io.github.zzih.rudder.task.api.task.executor;

import io.github.zzih.rudder.common.enums.datatype.DataType;
import io.github.zzih.rudder.common.param.Property;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * 把 {@link Property} 列表按 type 强类型绑定到 {@link PreparedStatement} 的 {@code ?} 槽位。
 * <p>
 * 类型映射:
 * <ul>
 *   <li>VARCHAR  → {@link PreparedStatement#setString}</li>
 *   <li>INTEGER  → {@link PreparedStatement#setInt}</li>
 *   <li>LONG     → {@link PreparedStatement#setLong}</li>
 *   <li>FLOAT    → {@link PreparedStatement#setFloat}</li>
 *   <li>DOUBLE   → {@link PreparedStatement#setDouble}</li>
 *   <li>BOOLEAN  → {@link PreparedStatement#setBoolean}</li>
 *   <li>DATE     → {@link PreparedStatement#setDate} (yyyy-MM-dd 字符串解析)</li>
 *   <li>TIME     → {@link PreparedStatement#setTime}</li>
 *   <li>TIMESTAMP→ {@link PreparedStatement#setTimestamp}</li>
 *   <li>LIST     → 不应到这一步(SqlPreprocessor 已展开成多个标量 Property);抛 IllegalState</li>
 *   <li>FILE     → 不支持(Rudder 已声明不做文件参数)</li>
 * </ul>
 * <p>
 * value 为 null 时统一走 {@link PreparedStatement#setNull} + 对应 {@link Types}。
 * value 是空串且类型不是 VARCHAR 时按 setNull 处理 — 跟 DS 行为一致,避免 {@code Integer.parseInt("")} 抛 NumberFormatException。
 */
public final class SqlParamBinder {

    private SqlParamBinder() {
    }

    public static void bindAll(PreparedStatement stmt, List<Property> binds) throws SQLException {
        if (binds == null || binds.isEmpty()) {
            return;
        }
        int idx = 1;
        for (Property p : binds) {
            bind(stmt, idx++, p);
        }
    }

    public static void bind(PreparedStatement stmt, int idx, Property property) throws SQLException {
        DataType type = property.getType() != null ? property.getType() : DataType.VARCHAR;
        String value = property.getValue();

        if (value == null || (value.isEmpty() && type != DataType.VARCHAR)) {
            stmt.setNull(idx, jdbcType(type));
            return;
        }

        try {
            switch (type) {
                case VARCHAR -> stmt.setString(idx, value);
                case INTEGER -> stmt.setInt(idx, Integer.parseInt(value));
                case LONG -> stmt.setLong(idx, Long.parseLong(value));
                case FLOAT -> stmt.setFloat(idx, Float.parseFloat(value));
                case DOUBLE -> stmt.setDouble(idx, Double.parseDouble(value));
                case BOOLEAN -> stmt.setBoolean(idx, Boolean.parseBoolean(value));
                case DATE -> stmt.setDate(idx, Date.valueOf(value));
                case TIME -> stmt.setTime(idx, Time.valueOf(value));
                case TIMESTAMP -> stmt.setTimestamp(idx, Timestamp.valueOf(value));
                case LIST ->
                    throw new IllegalStateException("LIST type should be expanded by SqlPreprocessor before bind");
                case FILE ->
                    throw new IllegalStateException("FILE type is not supported in SQL bind");
            }
        } catch (IllegalArgumentException e) {
            // 覆盖 NumberFormatException(parseInt/parseLong 等)+ Date.valueOf / Time.valueOf 抛出的格式异常
            throw new SQLException(
                    "Failed to bind param '" + property.getProp() + "' as " + type + ": value=" + value, e);
        }
    }

    private static int jdbcType(DataType type) {
        return switch (type) {
            case VARCHAR -> Types.VARCHAR;
            case INTEGER -> Types.INTEGER;
            case LONG -> Types.BIGINT;
            case FLOAT -> Types.FLOAT;
            case DOUBLE -> Types.DOUBLE;
            case BOOLEAN -> Types.BOOLEAN;
            case DATE -> Types.DATE;
            case TIME -> Types.TIME;
            case TIMESTAMP -> Types.TIMESTAMP;
            case LIST, FILE -> Types.NULL;
        };
    }
}
