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
import io.github.zzih.rudder.common.utils.bean.BeanConvertUtils;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;

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

    /** 从 MP IPage 直接构造,自动走 BeanUtils 浅拷贝(同构镜像 + 同类型 enum)。 */
    public static <S, T> PageResult<T> of(IPage<S> page, Class<T> targetClass) {
        return of(BeanConvertUtils.convertList(page.getRecords(), targetClass),
                page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 自定义 mapper 版本:enum 跨包或字段类型不一致时用,典型搭配 BeanConvertUtils.convertViaJson。 */
    public static <S, T> PageResult<T> of(IPage<S> page, Function<? super S, ? extends T> mapper) {
        return of(page.getRecords().stream().<T>map(mapper).toList(),
                page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
