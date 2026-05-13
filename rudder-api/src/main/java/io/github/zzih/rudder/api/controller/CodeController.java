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

package io.github.zzih.rudder.api.controller;

import io.github.zzih.rudder.api.security.annotation.RequireLoggedIn;
import io.github.zzih.rudder.common.result.Result;
import io.github.zzih.rudder.common.utils.naming.CodeGenerateUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 雪花 ID 生成接口,前端通过此接口获取全局唯一的 code。 */
@RestController
@RequestMapping("/api/codes")
@RequireLoggedIn
public class CodeController {

    @GetMapping("/generate")
    public Result<List<Long>> generate(@RequestParam(defaultValue = "1") int count) {
        if (count < 1 || count > 100) {
            count = 1;
        }
        List<Long> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            codes.add(CodeGenerateUtils.genCode());
        }
        return Result.ok(codes);
    }
}
