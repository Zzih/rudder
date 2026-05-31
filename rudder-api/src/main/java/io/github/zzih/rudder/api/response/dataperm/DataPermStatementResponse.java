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

package io.github.zzih.rudder.api.response.dataperm;

import java.util.List;

import lombok.Data;

/** 权限包作用域块出参:scope + 操作分组 + 多条库表行。 */
@Data
public class DataPermStatementResponse {

    private Long id;
    private Long scopeCode;
    private String scopeName;
    private List<Long> groupIds;
    private List<String> groupNames;
    private List<DataPermResourcePathResponse> resources;
}
