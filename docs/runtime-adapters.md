# Runtime 适配器

> Runtime SPI 把"任务在哪里跑"抽象成可插拔的执行环境。当前 active runtime 决定特定 TaskType 是走原生 Task 实现(JDBC / spark-submit)还是被替换成云上子类(Aliyun SDK / AWS SDK)。本章覆盖契约、三个内置 provider 的差异、扩展方法。

## 为什么有 Runtime 这一层

执行引擎和"运行环境"是两个概念:同一个 Spark JAR,可以跑在自建 EMR、阿里云 Serverless Spark、AWS EMR Serverless 上,提交方式、资源管理、状态查询都不同。

Rudder 的处理方式:**云上任务 = 原生 Task 的子类**。例如 `AliyunSparkSqlTask extends SparkSqlTask`,override `init() / handle() / cancel()` 调云端 SDK,其它(`resultSink` / `params` / `status`)全部继承自父类。Worker 永远调 `task.handle()`,不感知是原生还是云上版本。

Runtime 只负责声明「我接管哪个 TaskType,用哪个子类来跑」。

## 契约

### `EngineRuntime`

```java
public interface EngineRuntime extends AutoCloseable {
    String                       provider();
    Map<String, String>          envVars();             // 注入到 ctx,Shell/Python/JAR 可读
    Optional<TaskFactory>        taskFactoryFor(TaskType type);
    HealthStatus                 healthCheck();
    @Override default void close() {}
}
```

- `taskFactoryFor(type)` 返回非空 → Worker 用该 factory 造任务(云上子类)
- 返回空 → Worker 用 channel 默认工厂(原生 Task)
- `envVars` 不论是否接管都注入到 `TaskExecutionContext`

### `TaskFactory`

```java
@FunctionalInterface
public interface TaskFactory {
    Task create(TaskExecutionContext ctx) throws TaskException;
}
```

工厂收 ctx,返回 Task 实例(原生或云子类)。Worker 拿到后走标准生命周期:
`pipeline.injectResources(task) → task.init(ctx) → task.handle()`

### `AbstractEngineRuntime` + `Binding<P>`

云上 Runtime 直接继承基类,用 `bind()` 列出接管关系,基类负责 `paramsJson` 反序列化 + 按 TaskType 索引:

```java
public class AliyunRuntime extends AbstractEngineRuntime {
    public AliyunRuntime(AliyunRuntimeProperties props, Map<String,String> envVars,
                         VvpClient vvpClient, SparkClient sparkClient) {
        super(PROVIDER_KEY, envVars, List.of(
                bind(SPARK_SQL,  SqlTaskParams.class,      (ctx, p) -> new AliyunSparkSqlTask(ctx, p, props, sparkClient)),
                bind(SPARK_JAR,  SparkJarTaskParams.class, (ctx, p) -> new AliyunSparkJarTask(ctx, p, props, sparkClient)),
                bind(FLINK_SQL,  SqlTaskParams.class,      (ctx, p) -> new AliyunFlinkSqlTask(ctx, p, props, vvpClient)),
                bind(FLINK_JAR,  FlinkJarTaskParams.class, (ctx, p) -> new AliyunFlinkJarTask(ctx, p, props, vvpClient))));
    }
}
```

`bind(TaskType, Class<P>, BiFunction<ctx, P, Task>)`:
- `Class<P>` 用于反序列化 `ctx.paramsJson` 成 P
- BiFunction 拿到 ctx + P 构造云端 Task 子类
- 重复 TaskType 注册抛 `IllegalStateException`

### `EngineRuntimeProvider`

```java
public interface EngineRuntimeProvider extends ConfigurablePluginProviderFactory<ProviderContext> {
    @Override default String type() { return "runtime"; }

    EngineRuntime create(ProviderContext ctx, Map<String, String> config);

    default void closeResources() {}
}
```

- `create()` 用平台管理页填入的 config 构造单个 active EngineRuntime
- `closeResources()` 切换 provider 时由 `RuntimePluginManager` 调用,释放 SDK client 池等共享资源

## 内置 provider 一览

```
rudder-spi/rudder-runtime/
├── rudder-runtime-local       默认 runtime,不接管任何 TaskType
├── rudder-runtime-aliyun      接管 SPARK_SQL/JAR + FLINK_SQL/JAR(Serverless Spark + VVP)
└── rudder-runtime-aws         接管 SPARK_SQL/JAR + FLINK_SQL/JAR(EMR Serverless + Managed Flink)
```

|  | LOCAL | ALIYUN | AWS |
|:---|:---|:---|:---|
| Spark SQL | 原生 SparkSqlTask(JDBC Thrift Server) | AliyunSparkSqlTask(EMR Serverless) | AwsSparkSqlTask(EMR Serverless) |
| Spark JAR | 原生 SparkJarTask(`spark-submit`) | AliyunSparkJarTask(EMR Serverless) | AwsSparkJarTask(EMR Serverless) |
| Flink SQL | 原生 FlinkSqlTask(JDBC SQL Gateway) | AliyunFlinkSqlTask(VVP) | AwsFlinkSqlTask(Managed Flink) |
| Flink JAR | 原生 FlinkJarTask(`flink run`) | AliyunFlinkJarTask(VVP) | AwsFlinkJarTask(Managed Flink) |
| 凭证 | 本机环境(envVars) | AccessKey + Region | AWS SDK 默认凭证链 |

## LOCAL provider

`rudder-runtime-local`,默认选择,不接管任何 TaskType。所有任务走原生 Task 自跑路径(JDBC / shell / process)。仅暴露 `envVars` 给 Shell / Python / JAR 类任务读取。

**配置**:

| key | 必填 | 说明 |
|:---|:---:|:---|
| `envVars` | | 一行一个 `KEY=VALUE`,空行 / `#` 注释跳过。注入到所有任务的 `TaskExecutionContext.envVars` |

**适用**:开发 / 测试、自建 EMR / Hadoop、不接云的合规场景。

## ALIYUN provider

`rudder-runtime-aliyun`,对接阿里云 Serverless 大数据。

**配置**(`config` 字段,`KEY=VALUE` 多行):

| key | 必填 | 说明 |
|:---|:---:|:---|
| `accessKeyId` | ✓ | RAM AccessKey |
| `accessKeySecret` | ✓ | RAM Secret |
| `regionId` | | 默认 `cn-hangzhou` |
| `spark.workspaceId` | (Spark 用) | EMR Serverless Spark workspace |
| `spark.resourceQueueId` | | 资源队列 |
| `flink.workspaceId` | (Flink 用) | VVP workspace |
| `flink.namespace` | | 默认 `default` |

**SDK**:

- `com.aliyun.emr_serverless_spark20230808.Client` — Serverless Spark
- `com.aliyun.ververica20220718.Client` — VVP Flink

**云端 Task 子类**(`spark/` + `flink/` 子目录):

- `AliyunSparkSqlTask` — `createSqlStatement` 提交 SQL,轮询完成,把 columns/rows 喂回父类 sink
- `AliyunSparkJarTask` — `startJobRun` 提交 JAR,轮询直到 jobRun 终态
- `AliyunFlinkSqlTask` — `createDeployment(SQL)` 提交;批任务轮询 + sink 写空数组(VVP 不直接返结果集)
- `AliyunFlinkJarTask` — `createDeployment(JAR)` 提交;流任务提交即返回,Worker 标 RUNNING

**Flink savepoint**:`VvpSavepointUtils`(包内私有)封装 VVP 的 trigger savepoint API。

**资源释放**:阿里云 `tea-openapi` Client 不暴露 close API,旧实例靠 GC 回收 OkHttp 连接池。`closeResources()` 是 no-op。

## AWS provider

`rudder-runtime-aws`,对接 AWS Serverless 大数据。

**配置**(同 KEY=VALUE 多行):

| key | 必填 | 说明 |
|:---|:---:|:---|
| `region` | | 默认 `us-east-1` |
| `spark.applicationId` | (Spark 用) | EMR Serverless Application ID(预先建好的) |
| `spark.executionRoleArn` | (Spark 用) | IAM Role |
| `flink.serviceExecutionRole` | (Flink 用) | IAM Role |
| `flink.s3Bucket` | (Flink 用) | Flink artifact / state 存储桶 |
| `flink.runtimeEnvironment` | | Flink 版本,如 `FLINK-1_18` |

**SDK**:

- `software.amazon.awssdk.services.emrserverless.EmrServerlessClient`
- `software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2Client`(Managed Service for Flink)

**云端 Task 子类**(`spark/` + `flink/` 子目录):

- `AwsSparkSqlTask` — 提交 `spark-sql -e` 到 EMR Serverless,轮询完成,sink 写空数组(EMR 不直接返结果集)
- `AwsSparkJarTask` — `startJobRun` 提交 JAR,轮询直到 jobRun 终态
- `AwsFlinkSqlTask` — `createApplication + startApplication` 提交 SQL;批轮询到 READY,sink 写空数组
- `AwsFlinkJarTask` — `createApplication + startApplication` 提交 JAR;流任务提交即返回

**凭证**:AWS SDK 默认凭证链 — env / instance profile / SSO,不在 config 里写死 access key(RAM Role 部署最优)。

**资源释放**:`AwsRuntimeProvider.closeResources()` 调 `EmrServerlessClient.close()` / `KinesisAnalyticsV2Client.close()`,回收 SDK 内部线程池。

## 派发流程

`TaskWorker.executeTask` 的核心 4 行:

```java
TaskFactory factory = runtimeConfigService.taskFactoryFor(ctx.getTaskType())
        .orElse(channel::createNewTask);   // active runtime 接管 → 云子类工厂;否则 channel 默认
task = factory.create(ctx);
pipeline.injectResources(task, injCtx);   // ResultSink / DataSource 注入(子类继承,通用)
task.init(ctx);
task.handle();                             // 唯一执行入口,无分叉
```

收尾时按接口读出口状态(`ResultableTask.getResultPath` / `JobTask.getAppId` 等),云子类与原生 Task 完全对称。

## 切换 provider

UI:`管理 → Runtime → 编辑 / 新建`

```
1. 管理员保存新配置
2. RuntimeConfigService.save() invalidate cache
3. 旧 provider.closeResources()(如果切到不同 provider)
4. 下一个任务 dispatch 时 active() 重建 EngineRuntime,立即生效
```

不重启进程。运行中的任务继续用旧的 SDK client 引用(已被 Task 子类 closure 捕获),不受影响。

## 写一个新 provider:示例步骤

以「腾讯云 EMR Serverless」为例:

1. 建模块 `rudder-runtime-tencent`,依赖 `rudder-runtime-api` + `rudder-task-spark` + `rudder-task-flink` + 腾讯云 SDK
2. `TencentRuntimeProperties` POJO 描述配置(`@Data` lombok)
3. 按 `spark/` `flink/` 子目录写云上 Task 子类:
   - `TencentSparkSqlTask extends SparkSqlTask`,override `init/handle/cancel` 调腾讯 SDK
   - `TencentSparkJarTask extends SparkJarTask`,同理
   - Flink 两个同理
4. `TencentRuntime extends AbstractEngineRuntime`,构造里 `super(PROVIDER_KEY, envVars, List.of(bind(...) × 4))`
5. `@AutoService(EngineRuntimeProvider.class)` 标注的 `TencentRuntimeProvider`
   - `getProvider() → "TENCENT"`
   - `params() → List<PluginParamDefinition>`
   - `create(ctx, config)` 构造 SDK client + envVars + 返回 TencentRuntime
6. 在 `rudder-bundle-execution/pom.xml` 加依赖
7. 在 `src/main/resources/spi-guide/runtime-tencent.zh-CN.md` 写接入指南
8. ArchUnit 测试通过 → mvn install

详细规范见 [SPI 开发指南](spi-guide.md)。

## 排障

| 症状 | 排查 |
|:---|:---|
| 切到 ALIYUN 后任务一直 PENDING | AccessKey 是否有 EMR / VVP 权限;`workspaceId` / `namespace` 是否配对;网络是否能到 region endpoint |
| AWS provider 报 `region not found` | 拼写错误(如 `cn-northwest-1` vs `cn-northwest`);SDK 版本太老 |
| Spark/Flink JAR 提交后日志空 | 子进程 stdout 重定向写文件;Execution 日志路径检查 `<HOME>/logs/tasks/` |
| 切 provider 后旧 SDK 报 `connection closed` | 旧 client 已被 `closeResources()`,正常现象;在飞中的请求会失败,Worker 兜底重试 |
| 启动期 `Duplicate TaskFactory for X in provider Y` | 同一 provider 给同一 TaskType 注册了两次 binding,检查 Runtime 子类构造的 `List.of(bind...)` |
| 启动期 `paramsJson is missing for TaskType X` | 任务 `content` 字段为空,Worker 没正确 build paramsJson;检查 `TaskInstance.content` |

## 监控

各 provider `healthCheck()` 暴露在 admin UI 的「Runtime 状态」卡片。Prometheus(规划中):

```
rudder_runtime_submit_total{provider, taskType, status}
rudder_runtime_submit_duration_seconds{provider, taskType}
rudder_runtime_active{provider}
```

## 相关文档

- [SPI 开发指南](spi-guide.md) — 通用 SPI 装配规范
- [任务类型](task-types.md) — TaskType 与 Runtime 接管关系
- [配置参考](configuration.md#十五spi-provider-配置不在这里) — Runtime 配置落 `t_r_runtime_config`
- [部署指南](deployment.md) — Execution 工作目录 / 镜像内置驱动
