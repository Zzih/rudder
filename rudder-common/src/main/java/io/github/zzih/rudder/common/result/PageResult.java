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

package io.github.zzih.rudder.common.result;

import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.i18n.I18n;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class PageResult<T> implements Serializable {

    private int code;
    private String message;
    private List<T> data;
    private long total;
    private int pageNum;
    private int pageSize;
    private long timestamp;

    public PageResult() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> PageResult<T> of(List<T> data, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setCode(SystemErrorCode.SUCCESS.getCode());
        result.setMessage(I18n.t(SystemErrorCode.SUCCESS.getMessage()));
        result.setData(data);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }
}
