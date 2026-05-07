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

/**
 * 错误码契约。各领域 enum 实现这个接口,新增请按下面的 code 范围分配,**避免跨领域 code 冲突**。
 *
 * <h2>Code 范围分配</h2>
 *
 * <ul>
 *   <li><b>200-599</b> —— HTTP 状态码语义复用({@code SystemErrorCode})</li>
 *   <li><b>1000-1999</b> —— Workspace / 用户 / 成员 / Auth({@code WorkspaceErrorCode})</li>
 *   <li><b>2000-2999</b> —— Datasource({@code DatasourceErrorCode})</li>
 *   <li><b>3000-3999</b> —— Script / Task instance / Dispatch({@code ScriptErrorCode})</li>
 *   <li><b>4000-4999</b> —— Workflow / 发布 / 版本({@code WorkflowErrorCode})</li>
 *   <li><b>5000-5999</b> —— Approval({@code ApprovalErrorCode})</li>
 *   <li><b>6000-6999</b> —— Platform Config / SPI 未就绪({@code ConfigErrorCode})</li>
 *   <li><b>7000-7999</b> —— Task SPI({@code TaskErrorCode})</li>
 *   <li><b>8000-8999</b> —— AI / RAG / 方言({@code AiErrorCode})</li>
 *   <li><b>9000-9099</b> —— MCP({@code McpErrorCode})</li>
 *   <li><b>9100-9199</b> —— SPI provider({@code SpiErrorCode})</li>
 *   <li><b>9200-9299</b> —— File 操作({@code FileErrorCode})</li>
 * </ul>
 *
 * <p>规则:同一 code 必须 globally 唯一,跨枚举禁止重号,方便日志/排障按 code 单值定位领域。
 *
 * <h2>i18n 约定</h2>
 *
 * <p>{@link #getMessage()} 返回的是 <b>i18n key</b>(形如 {@code err.<EnumSimpleName>.<NAME>}),
 * 不是文案本身。运行时由 handler 通过 {@code I18n.t(key, args)} 在
 * {@code errors_<lang>.properties} 中查找本地化文案。
 *
 * <p>不在 enum 字段里硬编码英文/中文文案 —— 文案的唯一真理来源是 properties 文件,
 * IDE 在 enum 字段处 Cmd+Click key 字符串可直接跳到对应 properties 行。
 */
public interface ErrorCode {

    int getCode();

    /** i18n key — 形如 {@code err.<EnumSimpleName>.<NAME>}。 */
    String getMessage();
}
