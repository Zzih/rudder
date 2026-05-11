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

package io.github.zzih.rudder.common.utils.bean;

import io.github.zzih.rudder.common.utils.json.JsonUtils;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 通用对象转换工具类。
 * 基于 Spring BeanUtils.copyProperties，按同名属性自动映射。
 * 目标类中不存在的字段自动忽略（如 Entity 有 credential，DTO 没有则不会拷贝）。
 */
public final class BeanConvertUtils {

    private BeanConvertUtils() {
    }

    /** 缓存无参构造器，避免每次反射查找 */
    private static final ConcurrentHashMap<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /**
     * 单个对象转换。
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        T target = newInstance(targetClass);
        BeanUtils.copyProperties(source, target);
        return target;
    }

    /**
     * 列表转换。
     */
    public static <T> List<T> convertList(List<?> sourceList, Class<T> targetClass) {
        if (sourceList == null || sourceList.isEmpty()) {
            return Collections.emptyList();
        }
        return sourceList.stream()
                .map(source -> convert(source, targetClass))
                .toList();
    }

    /** 源/目标的嵌套字段或枚举来自不同包(同构镜像)时用此方法;{@link #convert} 走 Spring 浅拷贝会静默跳过跨类型字段。 */
    public static <T> T convertViaJson(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        return JsonUtils.convertValue(source, targetClass);
    }

    /**
     * MyBatis-Plus 分页转换。
     */
    public static <T> IPage<T> convertPage(IPage<?> sourcePage, Class<T> targetClass) {
        if (sourcePage == null) {
            return null;
        }
        return sourcePage.convert(record -> convert(record, targetClass));
    }

    @SuppressWarnings("unchecked")
    private static <T> T newInstance(Class<T> targetClass) {
        try {
            Constructor<?> constructor = CONSTRUCTOR_CACHE.computeIfAbsent(targetClass, clz -> {
                try {
                    Constructor<?> ctor = clz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    return ctor;
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(
                            "No default constructor found for " + clz.getSimpleName(), e);
                }
            });
            return (T) constructor.newInstance();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create instance of " + targetClass.getSimpleName(), e);
        }
    }
}
