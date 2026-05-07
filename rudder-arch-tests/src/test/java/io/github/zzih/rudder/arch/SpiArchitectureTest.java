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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * SPI 架构约束。
 *
 * <p>Provider 模块（{@code io.github.zzih.rudder.{file,ai,approval,metadata,notification,version,runtime,task,result}.<impl>}）
 * 必须是无 Spring 依赖的纯 POJO，通过 {@link java.util.ServiceLoader} 发现。这里用 ArchUnit 强制这条约束，
 * 防止有人偷懒加 {@code @Component} / {@code @Autowired} 让代码能跑但破坏 SPI 可丢入性。
 */
@AnalyzeClasses(packages = "io.github.zzih.rudder", importOptions = {ImportOption.DoNotIncludeTests.class})
public class SpiArchitectureTest {

    /** Provider 模块（非 api 包）不得使用 {@code @Component} / {@code @Service} / {@code @Repository}。 */
    @ArchTest
    static final ArchRule providers_must_not_use_spring_stereotype =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.zzih.rudder.file.local..",
                            "io.github.zzih.rudder.file.hdfs..",
                            "io.github.zzih.rudder.file.oss..",
                            "io.github.zzih.rudder.file.s3..",
                            "io.github.zzih.rudder.llm.claude..",
                            "io.github.zzih.rudder.llm.openai..",
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
                            "io.github.zzih.rudder.task.starrocks..",
                            "io.github.zzih.rudder.task.trino..",
                            "io.github.zzih.rudder.task.python..",
                            "io.github.zzih.rudder.task.shell..",
                            "io.github.zzih.rudder.task.seatunnel..",
                            "io.github.zzih.rudder.task.spark..",
                            "io.github.zzih.rudder.task.flink..",
                            "io.github.zzih.rudder.result.json..",
                            "io.github.zzih.rudder.result.csv..",
                            "io.github.zzih.rudder.result.avro..",
                            "io.github.zzih.rudder.result.orc..",
                            "io.github.zzih.rudder.result.parquet..")
                    .should()
                    .beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould()
                    .beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould()
                    .beAnnotatedWith("org.springframework.stereotype.Repository");

    /** Provider 模块不得使用字段注入 {@code @Autowired} / {@code @Value} / {@code @ConfigurationProperties}。 */
    @ArchTest
    static final ArchRule providers_must_not_use_spring_injection =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "io.github.zzih.rudder.file.local..",
                            "io.github.zzih.rudder.file.hdfs..",
                            "io.github.zzih.rudder.file.oss..",
                            "io.github.zzih.rudder.file.s3..",
                            "io.github.zzih.rudder.llm.claude..",
                            "io.github.zzih.rudder.llm.openai..",
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
                            "io.github.zzih.rudder.task.starrocks..",
                            "io.github.zzih.rudder.task.trino..",
                            "io.github.zzih.rudder.task.python..",
                            "io.github.zzih.rudder.task.shell..",
                            "io.github.zzih.rudder.task.seatunnel..",
                            "io.github.zzih.rudder.task.spark..",
                            "io.github.zzih.rudder.task.flink..",
                            "io.github.zzih.rudder.result.json..",
                            "io.github.zzih.rudder.result.csv..",
                            "io.github.zzih.rudder.result.avro..",
                            "io.github.zzih.rudder.result.orc..",
                            "io.github.zzih.rudder.result.parquet..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.boot.context.properties.ConfigurationProperties");

    /** 跨 provider 引用禁止：一个 provider 不得直接引用另一个 provider 的内部实现类。 */
    @ArchTest
    static final ArchRule providers_must_not_cross_reference_other_providers =
            noClasses()
                    .that()
                    .resideInAPackage("io.github.zzih.rudder.file.local..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "io.github.zzih.rudder.file.hdfs..",
                            "io.github.zzih.rudder.file.oss..",
                            "io.github.zzih.rudder.file.s3..");

    /**
     * 所有 PluginManager / ResultFormatRegistry 必须暴露 public 的 {@code getProviderDefinitions()} 方法，
     * 作为前端列表查询的统一入口。防止新加 SPI 时再起别的名字（如 availableNames / getChannelDefinitions）。
     */
    @ArchTest
    static final ArchRule plugin_managers_must_expose_getProviderDefinitions =
            classes()
                    .that()
                    .haveSimpleNameEndingWith("PluginManager")
                    .or()
                    .haveSimpleName("ResultFormatRegistry")
                    .should(haveGetProviderDefinitions());

    private static ArchCondition<JavaClass> haveGetProviderDefinitions() {
        return new ArchCondition<>("have public getProviderDefinitions() method (no-arg 或 Locale)") {

            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                // getAllMethods() includes methods inherited from AbstractConfigurablePluginRegistry;
                // 接受无参(TaskPluginManager / ResultFormatRegistry)或接受 Locale(i18n-aware registries)。
                boolean found = clazz.getAllMethods().stream()
                        .filter(m -> m.getName().equals("getProviderDefinitions"))
                        .filter(m -> m.getModifiers().contains(JavaModifier.PUBLIC))
                        .anyMatch(m -> {
                            var params = m.getRawParameterTypes();
                            return params.isEmpty()
                                    || (params.size() == 1 && params.get(0).getName().equals("java.util.Locale"));
                        });
                if (!found) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " must expose public getProviderDefinitions() method"));
                }
            }
        };
    }
}
