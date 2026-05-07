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

package io.github.zzih.rudder.service.workflow.controlflow.dependent;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DateValue {

    // hour
    CURRENT_HOUR("currentHour"),
    LAST_1_HOUR("last1Hour"),
    LAST_2_HOURS("last2Hours"),
    LAST_3_HOURS("last3Hours"),
    LAST_24_HOURS("last24Hours"),

    // day
    TODAY("today"),
    LAST_1_DAYS("last1Days"),
    LAST_2_DAYS("last2Days"),
    LAST_3_DAYS("last3Days"),
    LAST_7_DAYS("last7Days"),

    // week
    THIS_WEEK("thisWeek"),
    LAST_WEEK("lastWeek"),
    LAST_MONDAY("lastMonday"),
    LAST_TUESDAY("lastTuesday"),
    LAST_WEDNESDAY("lastWednesday"),
    LAST_THURSDAY("lastThursday"),
    LAST_FRIDAY("lastFriday"),
    LAST_SATURDAY("lastSaturday"),
    LAST_SUNDAY("lastSunday"),

    // month
    THIS_MONTH("thisMonth"),
    THIS_MONTH_BEGIN("thisMonthBegin"),
    LAST_MONTH("lastMonth"),
    LAST_MONTH_BEGIN("lastMonthBegin"),
    LAST_MONTH_END("lastMonthEnd");

    private final String value;

    DateValue(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static DateValue of(String value) {
        if (value == null) {
            return TODAY;
        }
        for (DateValue dv : values()) {
            if (dv.value.equalsIgnoreCase(value)) {
                return dv;
            }
        }
        return TODAY;
    }
}
