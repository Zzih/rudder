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

package io.github.zzih.rudder.service.script;

import io.github.zzih.rudder.common.enums.error.ScriptErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.exception.NotFoundException;
import io.github.zzih.rudder.dao.dao.ScriptDao;
import io.github.zzih.rudder.dao.entity.Script;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptDispatchService {

    private final ScriptDao scriptDao;

    /**
     * 校验后返回待分发的脚本。
     */
    public Script getScriptForDispatch(Long scriptCode) {
        Script script = scriptDao.selectByCode(scriptCode);
        if (script == null) {
            throw new NotFoundException(ScriptErrorCode.SCRIPT_NOT_FOUND);
        }
        if (script.getContent() == null || script.getContent().isBlank()) {
            throw new BizException(ScriptErrorCode.SCRIPT_CONTENT_EMPTY);
        }
        return script;
    }
}
