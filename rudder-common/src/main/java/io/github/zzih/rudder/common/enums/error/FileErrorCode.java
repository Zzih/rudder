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

package io.github.zzih.rudder.common.enums.error;

import io.github.zzih.rudder.common.result.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件操作错误码,占用 9200-9299。 */
@Getter
@AllArgsConstructor
public enum FileErrorCode implements ErrorCode {

    FILE_EMPTY(9201, "err.FileErrorCode.FILE_EMPTY"),
    FILE_TOO_LARGE_UPLOAD(9202, "err.FileErrorCode.FILE_TOO_LARGE_UPLOAD"),
    UPLOAD_FAILED(9203, "err.FileErrorCode.UPLOAD_FAILED"),
    FILE_NOT_FOUND(9204, "err.FileErrorCode.FILE_NOT_FOUND"),
    DIRECTORY_EXISTS(9205, "err.FileErrorCode.DIRECTORY_EXISTS"),
    RENAME_SOURCE_NOT_FOUND(9206, "err.FileErrorCode.RENAME_SOURCE_NOT_FOUND"),
    RENAME_TARGET_EXISTS(9207, "err.FileErrorCode.RENAME_TARGET_EXISTS"),
    FILE_EXISTS(9208, "err.FileErrorCode.FILE_EXISTS"),
    FILE_TYPE_NOT_EDITABLE(9209, "err.FileErrorCode.FILE_TYPE_NOT_EDITABLE"),
    FILE_TOO_LARGE_FOR_EDITING(9210, "err.FileErrorCode.FILE_TOO_LARGE_FOR_EDITING"),
    FILE_NAME_HAS_SEPARATOR(9211, "err.FileErrorCode.FILE_NAME_HAS_SEPARATOR"),
    PATH_REQUIRED(9212, "err.FileErrorCode.PATH_REQUIRED"),
    INVALID_PATH(9213, "err.FileErrorCode.INVALID_PATH");

    private final int code;
    private final String message;
}
