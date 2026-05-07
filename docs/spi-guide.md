# SPI 开发指南

> Rudder 的可插拔扩展点统一遵循 `-api / -<provider>` 结构，使用 `AbstractPluginRegistry` 加 Java `ServiceLoader` 装配。本章演示从零写一个新 provider。

## 一、SPI 一览

| 模块 | Family | 工厂接口 | PluginManager |
|:---|:---|:---|:---|
| `rudder-task` | `task` | `TaskChannelFactory` | `TaskPluginManager` |
| `rudder-runtime` | `runtime` | `EngineRuntimeProvider` | `RuntimePluginManager` |
| `rudder-metadata` | `metadata` | `MetadataClientFactory` | `MetadataPluginManager` |
| `rudder-llm` | `llm` | `LlmClientFactory` | `LlmPluginManager` |
| `rudder-embedding` | `embedding` | `EmbeddingClientFactory` | `EmbeddingPluginManager` |
| `rudder-vector` | `vector` | `VectorStoreFactory` | `VectorPluginManager` |
| `rudder-file` | `file` | `FileStorageFactory` | `FilePluginManager` |
| `rudder-approval` | `approval` | `ApprovalNotifierFactory` | `ApprovalPluginManager` |
| `rudder-notification` | `notification` | `NotificationChannelFactory` | `NotificationPluginManager` |
| `rudder-version` | `version` | `VersionStoreFactory` | `VersionPluginManager` |
| `rudder-result` | `result` | `ResultFormatFactory` | `ResultFormatRegistry` |

> `result` 是静态 SPI（Java SPI + `@AutoService`，不依赖 Spring），其余都是 Spring 托管的运行时可热切换 SPI。

## 二、装配规范（强制）

每个 SPI 模块必须遵守：

### 1. 模块命名

```
rudder-spi/rudder-<family>/
├── rudder-<family>-api          契约：interface + factory + PluginManager
├── rudder-<family>-local        本地 / 兜底实现（可选）
├── rudder-<family>-<provider>   每个外部服务一个独立模块
```

不允许：
- 把 provider 实现塞进 `-api`
- 把契约放在 provider 模块（破坏分层）
- 给某个 SPI 用一套不同的装配方式（统一性最重要）

### 2. 工厂接口

继承 `ConfigurablePluginProviderFactory<ProviderContext>`：

```java
public interface FooFactory extends ConfigurablePluginProviderFactory<ProviderContext> {

    @Override default String family() { return "foo"; }

    Foo create(ProviderContext ctx, Map<String, String> config);
}
```

`ConfigurablePluginProviderFactory` 提供：

- `getProvider()` — provider 名（如 `CLAUDE` / `AWS`），全大写
- `params()` — 返回 `List<PluginParamDefinition>` 描述配置项 schema（label / type / required / placeholder / encrypted）
- `validate(config)` — 配置合法性校验，返回 `ValidationResult`
- `family()` — 所属 SPI family
- `priority()` — 同 key 冲突时优先级（默认 0）
- `metadata()` — 版本 / 描述 / 文档 URL（用于 UI 展示）

### 3. PluginManager

放在 `-api` 模块，继承 `AbstractPluginRegistry`：

```java
@Component
public class FooPluginManager extends AbstractPluginRegistry<String, FooFactory> {

    public FooPluginManager() {
        super(FooFactory.class);   // ServiceLoader 加载 FooFactory 实现
    }

    @Override protected String keyOf(FooFactory f) { return f.getProvider(); }
}
```

注册流程：

```
PluginManager 启动 (@PostConstruct)
   ↓
ServiceLoader.load(FooFactory.class) 扫 META-INF/services
   ↓
按 keyOf(factory) 入 ConcurrentHashMap
   ↓ 冲突时
priority 大者胜出；平级 ERROR 告警保留后发现的
```

### 4. SPI 文件登记

在 provider 模块的 `src/main/resources/META-INF/services/<工厂全限定名>` 写入：

```
io.github.zzih.rudder.foo.bar.BarFooFactory
```

或者直接给工厂类打 `@AutoService(FooFactory.class)`（依赖 `com.google.auto.service`），构建时自动生成 SPI 文件。

## 三、`ProviderContext`

`ProviderContext` 是工厂构造实例时拿到的宿主上下文，含运行时依赖：

- 数据源解析（`DataSourceInfo getDatasource(id)`）
- 文件存储（`FileStorage`）
- 元数据 / LLM / Embedding 等其它 SPI 的句柄（按需）
- 工作空间 / 用户上下文

`ProviderContext` 由各 SPI 在 `service-server` 模块组装后传给 PluginManager；provider 实现只能依赖**契约级**的接口，不能反向引用 service / dao。这是 ArchUnit `SpiArchitectureTest` 守护的硬约束。

## 四、配置存储

平台级 provider 配置存在 `t_r_<family>_config`：

```
t_r_runtime_config
  ( id, name, provider, params_json, enabled, deleted_at, created_at, updated_at )
```

- `provider` = `factory.getProvider()`，如 `AWS`
- `params_json` = 用户在 UI 填写的 KV，结构由 `factory.params()` 决定
- 同一个 family 可同时存在多条配置（如同时配两个 LLM provider 用于路由）
- `enabled=true` 的配置才会被 PluginManager 实例化为 active client

## 五、热切换

UI 改了某条 `t_r_<family>_config`：

1. 后端 `<Family>ConfigService.update(...)` 写库
2. 调 `pluginManager.refresh(...)` —— 用新 config 调 `factory.create(ctx, config)` 拿到新实例
3. 用 `volatile` 引用做原子替换
4. 旧实例：调 `closeResources()`（如 SDK 提供 close）然后丢给 GC
5. 下一个调用方**立即**看到新实例

整个过程不重启进程。`@ConfigurablePluginProviderFactory` 的 SDK 不暴露 close API 时，按代码注释说明保留默认 no-op 即可（如阿里云 `tea-openapi` Client）。

## 六、新增 provider 示例：通知渠道

目标：新增飞书 / 钉钉 / Slack 之外的「企业微信」通知渠道。

### 1. 建模块

```
rudder-spi/rudder-notification/rudder-notification-wecom/
├── pom.xml
└── src/main/java/io/github/zzih/rudder/notification/wecom/
    ├── WeComNotificationChannel.java
    └── WeComNotificationChannelFactory.java
```

`pom.xml` 仅依赖 `rudder-notification-api` + 企业微信 SDK / HTTP 客户端。

### 2. 实现 NotificationChannel 契约

```java
public class WeComNotificationChannel implements NotificationChannel {

    private final String webhook;

    public WeComNotificationChannel(String webhook) { this.webhook = webhook; }

    @Override
    public void send(NotificationMessage msg) {
        // POST webhook，构造企业微信 markdown / text payload
    }

    @Override public String getProvider() { return "WECOM"; }

    @Override public boolean isHealthy() { /* 探活 */ }
}
```

### 3. 实现工厂

```java
@AutoService(NotificationChannelFactory.class)
public class WeComNotificationChannelFactory implements NotificationChannelFactory {

    @Override public String getProvider() { return "WECOM"; }

    @Override
    public List<PluginParamDefinition> params() {
        return List.of(
            PluginParamDefinition.builder()
                .name("webhook").label("Webhook URL").type("input")
                .required(true).encrypted(true)
                .placeholder("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=...")
                .build()
        );
    }

    @Override
    public NotificationChannel create(ProviderContext ctx, Map<String, String> config) {
        return new WeComNotificationChannel(config.get("webhook"));
    }
}
```

### 4. 注册到 bundle

`rudder-bundles/rudder-bundle-api/pom.xml` 加：

```xml
<dependency>
  <groupId>io.github.zzih</groupId>
  <artifactId>rudder-notification-wecom</artifactId>
  <version>${project.version}</version>
</dependency>
```

通知是平台级配置，只需 Server 装载；如果是任务执行类 SPI（task / runtime），还需要加到 `rudder-bundle-execution`。

### 5. 启动

`AbstractPluginRegistry.@PostConstruct` 自动扫到 `WeComNotificationChannelFactory`，admin UI 在 `通知渠道 → 新建` 时下拉框出现 `WECOM`。

## 七、新增 TaskType

`rudder-task` 比通用 SPI 多一步——`TaskType` 枚举增项 + 控制流不进 SPI。完整流程见 [任务类型 - 添加新 TaskType](task-types.md#添加新-tasktype)。

## 八、新增 Runtime provider

Runtime 模型:**云上任务 = 原生 Task 的子类**。Provider 用 `bind(TaskType, Class<P>, BiFunction)` 列出"我接管哪个 TaskType,用哪个子类来跑",基类 `AbstractEngineRuntime` 负责反序列化 paramsJson 并按 TaskType 索引。

```java
public class GcpRuntime extends AbstractEngineRuntime {
    public static final String PROVIDER_KEY = "GCP";

    public GcpRuntime(GcpRuntimeProperties props, Map<String, String> envVars, DataprocClient dpClient) {
        super(PROVIDER_KEY, envVars, List.of(
                bind(TaskType.SPARK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new GcpSparkSqlTask(ctx, p, props, dpClient)),
                bind(TaskType.SPARK_JAR, SparkJarTaskParams.class,
                        (ctx, p) -> new GcpSparkJarTask(ctx, p, props, dpClient)),
                bind(TaskType.FLINK_SQL, SqlTaskParams.class,
                        (ctx, p) -> new GcpFlinkSqlTask(ctx, p, props, dpClient)),
                bind(TaskType.FLINK_JAR, FlinkJarTaskParams.class,
                        (ctx, p) -> new GcpFlinkJarTask(ctx, p, props, dpClient))));
    }
}

@AutoService(EngineRuntimeProvider.class)
public class GcpRuntimeProvider implements EngineRuntimeProvider {

    @Override public String getProvider() { return GcpRuntime.PROVIDER_KEY; }

    @Override
    public List<PluginParamDefinition> params() { /* projectId / serviceAccount / envVars / ... */ }

    @Override
    public EngineRuntime create(ProviderContext ctx, Map<String, String> config) {
        GcpRuntimeProperties props = parse(config);
        Map<String, String> envVars = RuntimeConfigUtils.parseProperties(config.get("envVars"));
        DataprocClient dpClient = newClient(props);
        return new GcpRuntime(props, envVars, dpClient);
    }

    @Override
    public void closeResources() {
        // SDK 暴露 close 时调用,切换 provider 时由 RuntimePluginManager 调
    }
}
```

云上 Task 子类(继承自原生 Task)按 `spark/` `flink/` 子目录组织,override `init/handle/cancel`:

```java
public class GcpSparkSqlTask extends SparkSqlTask {
    private final GcpRuntimeProperties props;
    private final DataprocClient dpClient;

    public GcpSparkSqlTask(TaskExecutionContext ctx, SqlTaskParams params,
                           GcpRuntimeProperties props, DataprocClient dpClient) {
        super(ctx, params);
        this.props = props;
        this.dpClient = dpClient;
    }

    @Override public void init() { this.status = TaskStatus.RUNNING; }   // 跳过父类 JDBC 连接

    @Override public void handle() throws TaskException {
        // 调 GCP SDK 提交 + 轮询 + 把结果写到继承自父类的 protected resultSink
    }

    @Override public void cancel() throws TaskException {
        this.status = TaskStatus.CANCELLED;
        try { /* 调 GCP cancel API */ }
        finally { /* 兜底 close resultSink */ }
    }
}
```

详见 [Runtime 适配器](runtime-adapters.md)。

## 九、Provider 文档（接入指南）

在 provider 模块 classpath 放 markdown：

```
src/main/resources/spi-guide/<family>-<provider>.zh-CN.md
src/main/resources/spi-guide/<family>-<provider>.en-US.md
```

带 YAML front-matter：

```markdown
---
description: AWS EMR Serverless + Managed Flink
---

## 必填字段

| key | 含义 |
|:---|:---|
| region | AWS Region |
| spark.applicationId | EMR Serverless Application ID |
...
```

`SpiGuideLoader` 会按当前用户 locale 加载，渲染到 admin UI 的右侧抽屉，作为 provider 配置说明。

## 十、ArchUnit 守护

`rudder-arch-tests` 包含 `SpiArchitectureTest`，跑 `mvn test` 会校验：

- `-api` 不依赖 `-<provider>`
- 任何 SPI 模块不依赖 `rudder-service`
- provider 模块不依赖其它 provider 模块（强制平等）
- 工厂接口 / PluginManager 命名规范

写新 SPI 通过这层校验才能合入。

## 十一、最佳实践

| 主题 | 建议 |
|:---|:---|
| 凭证 | 用 `PluginParamDefinition.encrypted=true`，由前端 / 后端走 AES 加密落库 |
| 健康检查 | 实现 `isHealthy()` / `healthCheck()`，admin UI 探活展示状态 |
| 资源释放 | SDK 暴露 close 时实现 `closeResources()`，否则保留默认 |
| 并发 | provider 实例可能被多线程并发使用，避免共享可变状态 |
| 日志 | 用 `slf4j`，不要直接 stdout；日志路径会被全局脱敏 |
| 错误处理 | 抛 `ProviderConfigException` / `ProviderExecutionException`，PluginManager 会包成统一响应 |
| 测试 | `-api` 模块只放契约的 unit test；provider 模块跑集成 test（TestContainers） |
| 版本 | `metadata().version()` 跟 SDK 大版本同步，方便排障 |
| 中英文 | UI label / placeholder 走 `params()` 返回的多语言字段；guide 走 `<provider>.zh-CN.md` / `<provider>.en-US.md` |

## 相关文档

- [架构总览](architecture.md) — 模块依赖与分层
- [配置参考](configuration.md#十五spi-provider-配置不在这里) — provider 配置落表说明
- [任务类型](task-types.md) — `rudder-task` 的特化
- [Runtime 适配器](runtime-adapters.md) — `rudder-runtime` 的特化
