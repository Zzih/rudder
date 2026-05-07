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

package io.github.zzih.rudder.service.download;

import io.github.zzih.rudder.common.enums.error.SystemErrorCode;
import io.github.zzih.rudder.common.exception.BizException;
import io.github.zzih.rudder.common.utils.bean.EnumUtils;

import java.io.OutputStream;

/**
 * 用户下载结果时可选的文件格式。跟存储侧 {@code ResultFormat} 是两件事 ——
 * 这里只关心面向用户的"装箱":CSV 要 BOM 兼容 Excel,XLSX 要表头样式/列宽/冻结。
 */
public enum DownloadFormat {

    CSV("text/csv; charset=UTF-8", ".csv") {

        @Override
        public ResultDownloadWriter newWriter(OutputStream out) {
            return new CsvDownloadWriter(out);
        }
    },
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx") {

        @Override
        public ResultDownloadWriter newWriter(OutputStream out) {
            return new ExcelDownloadWriter(out);
        }
    };

    private final String contentType;
    private final String extension;

    DownloadFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public abstract ResultDownloadWriter newWriter(OutputStream out);

    /** 不区分大小写查找;未知 format 抛 400,避免静默退化让用户拿到错格式的文件。 */
    public static DownloadFormat of(String name) {
        return EnumUtils.lookupByName(DownloadFormat.class, name)
                .orElseThrow(() -> new BizException(SystemErrorCode.BAD_REQUEST,
                        "Unsupported download format: " + name));
    }
}
