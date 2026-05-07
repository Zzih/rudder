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

package io.github.zzih.rudder.common.enums.redaction;

/**
 * 脱敏策略执行器类型。与 {@code t_r_redaction_strategy.executor_type} 的字符串值一一对应。
 */
public enum RedactionExecutorType {
    /** 正则 match + 带反向引用的 replacement 模板。 */
    REGEX_REPLACE,
    /** 保留前 N 后 M,中间填 mask_char。 */
    PARTIAL,
    /** 整体替换为固定字符串。 */
    REPLACE,
    /** SHA256 截取前 N 位。 */
    HASH,
    /** 置 null。 */
    REMOVE
}
