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

package io.github.zzih.rudder.rpc.server;

import java.lang.reflect.Method;

/**
 * 封装一个 RPC 方法的反射调用。
 */
public class MethodInvoker {

    private final Object bean;
    private final Method method;

    public MethodInvoker(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
        this.method.setAccessible(true);
    }

    public Object invoke(Object... args) throws Exception {
        return method.invoke(bean, args);
    }

    public Class<?>[] getParameterTypes() {
        return method.getParameterTypes();
    }

    public Class<?> getReturnType() {
        return method.getReturnType();
    }
}
