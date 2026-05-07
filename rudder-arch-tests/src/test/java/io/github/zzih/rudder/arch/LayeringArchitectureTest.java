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

package io.github.zzih.rudder.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 分层架构约束。约束**实际代码层级 vs 模块依赖方向**的对齐。
 *
 * <p>SPI 内部规范在 {@link SpiArchitectureTest},这里管:
 * <ul>
 *   <li>L0 common 不得依赖 Spring (是纯 JAR utility,不是 Spring 模块)</li>
 *   <li>L5 controller 不得反向接触 L3 dao (走 service 中介)</li>
 *   <li>L5 execution 不得直接持 dao 接口 (走 service-shared)</li>
 *   <li>L1 SPI -api 不得依赖业务模块 (datasource / service / dao)</li>
 *   <li>L2 SPI provider 不得依赖 dao.entity / dao.dao (provider 应通过 SPI context 拿数据)</li>
 * </ul>
 *
 * <p>这些规则是分阶段引入的"边界栅栏":写好后跑测试,失败列表 = Phase 1 修复 todo。
 * 修完一条违规即可启用对应规则。当前阶段对**已知违规**,可加 {@code @ArchIgnore}
 * 或临时缩小 scope,逐条恢复。
 */
@AnalyzeClasses(packages = "io.github.zzih.rudder", importOptions = {ImportOption.DoNotIncludeTests.class})
public class LayeringArchitectureTest {

    // ====================================================================
    // L0: rudder-common 必须是无框架 JAR
    // ====================================================================

    /**
     * common 不得携带 Spring stereotype 注解。
     * 已知违规(待 Phase 1 修):SecurityConfigValidator / AuditMetaObjectHandler /
     * AuditLogAspect / AuditLogAsyncService / RateLimitService。
     */
    @ArchTest
    static final ArchRule common_must_not_be_spring_managed =
            noClasses()
                    .that().resideInAPackage("io.github.zzih.rudder.common..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                    .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration")
                    .orShould().beAnnotatedWith("org.aspectj.lang.annotation.Aspect");

    // ====================================================================
    // L5: HTTP controller 不得直连 DAO 层
    // ====================================================================

    /**
     * controller 不得 import {@code rudder-dao} 的接口或实体。
     * controller 应只依赖 service-server / service-shared 的业务接口和 DTO,
     * Request/Response 走 rudder-api/api/request/response 包,DTO 走 service.dto。
     */
    @ArchTest
    static final ArchRule controllers_must_not_depend_on_dao =
            noClasses()
                    .that().resideInAPackage("io.github.zzih.rudder.api.controller..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao.dao..")
                    .orShould().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao.entity..");

    /**
     * Response DTO 不得携带 DAO 实体字段。Response 应只用 BeanConvertUtils 从 service 层 DTO 转出。
     */
    @ArchTest
    static final ArchRule responses_must_not_expose_dao_entities =
            noClasses()
                    .that().resideInAPackage("io.github.zzih.rudder.api.response..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao.entity..");

    // ====================================================================
    // L5: execution 不得直接持 DAO 接口
    // ====================================================================

    /**
     * Execution 模块不得 import {@code rudder-dao.dao} 接口。
     * 应通过 service-shared 的业务 service 中介(TaskInstanceService 等)。
     * 已知违规:TaskWorker 直接持 TaskInstanceDao + TaskDefinitionDao。
     */
    @ArchTest
    static final ArchRule execution_must_not_depend_on_dao_interfaces =
            noClasses()
                    .that().resideInAPackage("io.github.zzih.rudder.execution..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao.dao..");

    // ====================================================================
    // L1: SPI -api 模块不得反向依赖业务层
    // ====================================================================

    /**
     * SPI 契约层不得 import 业务服务/数据源/DAO 模块。
     * 已知违规:metadata-api → datasource (DatasourceService + ConnectionPoolManager 暴露在 ProviderContext)。
     *
     * <p>注:SPI provider 模块(-local/-{provider})也用同包前缀,但因其位于子包(file.local..)
     * 不在此 scope 内。这里只锁 -api 的契约纯净度。各 SPI 的 -api 包形如:
     * {@code io.github.zzih.rudder.{file,result,llm,task,...}.api..}。
     */
    @ArchTest
    static final ArchRule spi_api_must_not_depend_on_business_layers =
            noClasses()
                    .that().resideInAnyPackage(
                            "io.github.zzih.rudder.spi.api..",
                            "io.github.zzih.rudder.file.api..",
                            "io.github.zzih.rudder.result.api..",
                            "io.github.zzih.rudder.task.api..",
                            "io.github.zzih.rudder.llm.api..",
                            "io.github.zzih.rudder.notification.api..",
                            "io.github.zzih.rudder.metadata.api..",
                            "io.github.zzih.rudder.approval.api..",
                            "io.github.zzih.rudder.embedding.api..",
                            "io.github.zzih.rudder.runtime.api..",
                            "io.github.zzih.rudder.vector.api..",
                            "io.github.zzih.rudder.version.api..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao..")
                    .orShould().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.datasource..")
                    .orShould().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.service..");

    // ====================================================================
    // L2: SPI provider 不得直接 import dao.entity / dao.dao
    // ====================================================================

    /**
     * SPI provider 模块不得反向依赖 DAO。
     * Provider 是被宿主装载的插件,应通过 SPI context (ProviderContext) 拿到所需数据,
     * 不能直接读 DAO entity / 调 DAO 接口。
     * 已知违规:metadata-jdbc / metadata-datahub / metadata-openmetadata 各自 import dao.entity.Datasource。
     */
    @ArchTest
    static final ArchRule spi_providers_must_not_depend_on_dao =
            noClasses()
                    .that().resideInAnyPackage(
                            "io.github.zzih.rudder.file.local..",
                            "io.github.zzih.rudder.file.hdfs..",
                            "io.github.zzih.rudder.file.oss..",
                            "io.github.zzih.rudder.file.s3..",
                            "io.github.zzih.rudder.llm.claude..",
                            "io.github.zzih.rudder.llm.openai..",
                            "io.github.zzih.rudder.llm.deepseek..",
                            "io.github.zzih.rudder.llm.ollama..",
                            "io.github.zzih.rudder.approval.local..",
                            "io.github.zzih.rudder.approval.lark..",
                            "io.github.zzih.rudder.approval.kissflow..",
                            "io.github.zzih.rudder.metadata.jdbc..",
                            "io.github.zzih.rudder.metadata.datahub..",
                            "io.github.zzih.rudder.metadata.openmetadata..",
                            "io.github.zzih.rudder.notification.lark..",
                            "io.github.zzih.rudder.notification.slack..",
                            "io.github.zzih.rudder.notification.dingtalk..",
                            "io.github.zzih.rudder.version.local..",
                            "io.github.zzih.rudder.version.git..",
                            "io.github.zzih.rudder.runtime.local..",
                            "io.github.zzih.rudder.runtime.aliyun..",
                            "io.github.zzih.rudder.runtime.aws..",
                            "io.github.zzih.rudder.task.mysql..",
                            "io.github.zzih.rudder.task.hive..",
                            "io.github.zzih.rudder.task.postgres..",
                            "io.github.zzih.rudder.task.starrocks..",
                            "io.github.zzih.rudder.task.trino..",
                            "io.github.zzih.rudder.task.clickhouse..",
                            "io.github.zzih.rudder.task.doris..",
                            "io.github.zzih.rudder.task.python..",
                            "io.github.zzih.rudder.task.shell..",
                            "io.github.zzih.rudder.task.http..",
                            "io.github.zzih.rudder.task.seatunnel..",
                            "io.github.zzih.rudder.task.spark..",
                            "io.github.zzih.rudder.task.flink..",
                            "io.github.zzih.rudder.result.json..",
                            "io.github.zzih.rudder.result.csv..",
                            "io.github.zzih.rudder.result.avro..",
                            "io.github.zzih.rudder.result.orc..",
                            "io.github.zzih.rudder.result.parquet..",
                            "io.github.zzih.rudder.vector.local..",
                            "io.github.zzih.rudder.vector.pgvector..",
                            "io.github.zzih.rudder.vector.qdrant..",
                            "io.github.zzih.rudder.embedding.openai..",
                            "io.github.zzih.rudder.embedding.zhipu..")
                    .should().dependOnClassesThat().resideInAPackage("io.github.zzih.rudder.dao..");
}
