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

package io.github.zzih.rudder.common.utils.placeholder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统内置参数定义 + 注入。和 DolphinScheduler 的 system.* 参数清单 1:1 对齐。
 *
 * <p>语义:这些 prop 在 Worker 端解析 SQL/Shell 占位符之前以**最低优先级**(putIfAbsent)注入到
 * paramMap,用户的 project/global/runtime/local 参数同名时会覆盖之 — DS 的优先级链尾。
 *
 * <p>所有 KEY 都集中在本类的常量区,新增 / 删除走此处。BuiltInContext 是 nullable 字段的载体
 * (任务在不同上下文下能拿到的信息不一定齐),build() 只把非 null 字段塞进结果 map。
 *
 * <p>时间相关字段({@code system.datetime} / {@code system.biz.curdate} / {@code system.biz.date})
 * 按 {@link BuiltInContext#baseTime} 算 — Worker 端用 instance.startedAt 作 base,
 * 没有 commandType 概念(Rudder 不做 DS 那种调度补跑语义)。biz.date = baseTime - 1d。
 */
public final class BuiltInParams {

    // ===== KEY 常量 =====

    /** 调度时间(任务实际执行的基准时间)→ {@code yyyyMMddHHmmss}。 */
    public static final String SYSTEM_DATETIME = "system.datetime";

    /** 当前业务日期(等于 baseTime 当天)→ {@code yyyyMMdd}。 */
    public static final String SYSTEM_BIZ_CURDATE = "system.biz.curdate";

    /** 业务日期(baseTime - 1d,T+1 ETL 标配)→ {@code yyyyMMdd}。 */
    public static final String SYSTEM_BIZ_DATE = "system.biz.date";

    public static final String SYSTEM_TASK_EXECUTE_PATH = "system.task.execute.path";
    public static final String SYSTEM_TASK_INSTANCE_ID = "system.task.instance.id";
    public static final String SYSTEM_TASK_DEFINITION_CODE = "system.task.definition.code";
    public static final String SYSTEM_TASK_DEFINITION_NAME = "system.task.definition.name";
    public static final String SYSTEM_WORKFLOW_INSTANCE_ID = "system.workflow.instance.id";
    public static final String SYSTEM_WORKFLOW_DEFINITION_CODE = "system.workflow.definition.code";
    public static final String SYSTEM_WORKFLOW_DEFINITION_NAME = "system.workflow.definition.name";
    public static final String SYSTEM_PROJECT_CODE = "system.project.code";
    public static final String SYSTEM_PROJECT_NAME = "system.project.name";

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BuiltInParams() {
    }

    /**
     * 按上下文产出内置参数 map。null 字段会被跳过 — caller 不必预先填充全部字段。
     * 返回 LinkedHashMap 保留插入顺序,便于审计日志。
     */
    public static Map<String, String> build(BuiltInContext ctx) {
        Map<String, String> m = new LinkedHashMap<>();
        if (ctx == null) {
            return m;
        }
        putIfNotNull(m, SYSTEM_TASK_INSTANCE_ID, ctx.taskInstanceId);
        putIfNotNull(m, SYSTEM_TASK_DEFINITION_CODE, ctx.taskDefinitionCode);
        putIfNotNull(m, SYSTEM_TASK_DEFINITION_NAME, ctx.taskDefinitionName);
        putIfNotNull(m, SYSTEM_WORKFLOW_INSTANCE_ID, ctx.workflowInstanceId);
        putIfNotNull(m, SYSTEM_WORKFLOW_DEFINITION_CODE, ctx.workflowDefinitionCode);
        putIfNotNull(m, SYSTEM_WORKFLOW_DEFINITION_NAME, ctx.workflowDefinitionName);
        putIfNotNull(m, SYSTEM_PROJECT_CODE, ctx.projectCode);
        putIfNotNull(m, SYSTEM_PROJECT_NAME, ctx.projectName);
        putIfNotNull(m, SYSTEM_TASK_EXECUTE_PATH, ctx.executePath);
        if (ctx.baseTime != null) {
            m.put(SYSTEM_DATETIME, ctx.baseTime.format(FMT_DATETIME));
            m.put(SYSTEM_BIZ_CURDATE, ctx.baseTime.format(FMT_DATE));
            m.put(SYSTEM_BIZ_DATE, ctx.baseTime.minusDays(1).format(FMT_DATE));
        }
        return m;
    }

    private static void putIfNotNull(Map<String, String> m, String key, Object value) {
        if (value != null) {
            String s = value.toString();
            if (!s.isEmpty()) {
                m.put(key, s);
            }
        }
    }

    /**
     * 内置参数装填上下文。所有字段都是 nullable — Worker 在拼装时按手头能拿到的信息填充,
     * 缺失字段不会进结果 map(占位符未解析时保留原样)。
     */
    public static final class BuiltInContext {

        private Long taskInstanceId;
        private Long taskDefinitionCode;
        private String taskDefinitionName;
        private Long workflowInstanceId;
        private Long workflowDefinitionCode;
        private String workflowDefinitionName;
        private Long projectCode;
        private String projectName;
        private String executePath;
        private LocalDateTime baseTime;

        public static BuiltInContext builder() {
            return new BuiltInContext();
        }

        public BuiltInContext taskInstanceId(Long v) {
            this.taskInstanceId = v;
            return this;
        }

        public BuiltInContext taskDefinitionCode(Long v) {
            this.taskDefinitionCode = v;
            return this;
        }

        public BuiltInContext taskDefinitionName(String v) {
            this.taskDefinitionName = v;
            return this;
        }

        public BuiltInContext workflowInstanceId(Long v) {
            this.workflowInstanceId = v;
            return this;
        }

        public BuiltInContext workflowDefinitionCode(Long v) {
            this.workflowDefinitionCode = v;
            return this;
        }

        public BuiltInContext workflowDefinitionName(String v) {
            this.workflowDefinitionName = v;
            return this;
        }

        public BuiltInContext projectCode(Long v) {
            this.projectCode = v;
            return this;
        }

        public BuiltInContext projectName(String v) {
            this.projectName = v;
            return this;
        }

        public BuiltInContext executePath(String v) {
            this.executePath = v;
            return this;
        }

        public BuiltInContext baseTime(LocalDateTime v) {
            this.baseTime = v;
            return this;
        }
    }
}
