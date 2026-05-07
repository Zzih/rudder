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

package io.github.zzih.rudder.spi.api.model;

/**
 * Factory 的 "测试连接" 结果。前端在保存配置前点 "Test" 调用。
 *
 * <p>与 {@link ValidationResult} 区别：validate 是字段级静态校验（是否填了必填项 / 格式对不对），
 * testConnection 会实际发起网络 / IO 动作验证 provider 是否真能工作（账号正确、端点可达等）。
 */
public record TestResult(Status status, String message, long elapsedMillis) {

    public enum Status {
        /** 测试成功，provider 配置可用 */
        SUCCESS,
        /** 测试失败，{@link #message} 含错误详情 */
        FAILED,
        /** 当前 provider 不支持测试，前端可隐藏 Test 按钮 */
        NOT_SUPPORTED
    }

    public static TestResult success(long elapsedMillis) {
        return new TestResult(Status.SUCCESS, "OK", elapsedMillis);
    }

    public static TestResult failed(String message, long elapsedMillis) {
        return new TestResult(Status.FAILED, message, elapsedMillis);
    }

    public static TestResult notSupported() {
        return new TestResult(Status.NOT_SUPPORTED, "testConnection not supported by this provider", 0L);
    }
}
