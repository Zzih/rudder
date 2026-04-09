# Rudder

**新一代流批一体大数据 IDE 平台**

*让每一位数据人都能在数据的海洋中自由掌舵。*

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)

[English](README_EN.md) | 中文

---

## 为什么要做 Rudder

我从事数据开发工作已有 8 年，接触大数据也有 6、7 年了。这些年里经历过手搭 Hadoop 集群、迁移云平台、对接各种调度系统，踩过的坑、绕过的弯路，慢慢沉淀成了对"一个好用的数据平台应该长什么样"的思考。

这个项目源于 5、6 年前的一个构想——做一个真正好用的开源大数据 IDE。但一个人的精力有限，后端架构想得再清楚，前端的 IDE 交互、DAG 编辑器、工作流可视化这些体验层的东西始终是瓶颈，项目也就一直停留在构想阶段。

直到今天，AI 辅助编程已经足够成熟，尤其在前端开发领域——复杂的组件交互、UI 细节打磨，AI 都能高效配合完成。这让一个人也有能力去撑起一个完整的全栈项目。所以我决定不再等了，把这些年的积累和思考付诸实践。

### 大数据部门的尴尬定位

大数据部门在企业中的定位一直比较割裂：小公司不需要，成本也承受不住；头部大公司能承受大数据的成本，但它们早已有了自己的云服务产品（DataWorks、EasyData 等）。真正痛苦的是夹在中间的那一批——有一定规模、有大数据部门的公司。

它们面临的困境是：

- **云平台不适配** — 想用云厂商的大数据平台（DataWorks、EasyData...），但发现和自己公司的业务流程、权限体系、审批规范对不上，二次开发的成本甚至比自建还高
- **开源项目太臃肿** — 看看 DataSphere Studio 等开源项目，功能确实全面，但模块太多、概念太重、部署运维复杂，很多功能用不上却不得不背着
- **自研周期长** — 从零搭一套数据平台，后端的任务调度、工作流引擎、多引擎适配已经够复杂了，再加上前端的 IDE 编辑器、DAG 可视化编排、权限管理界面，一个小团队很难在合理的时间内交付一个可用的产品

所以市场上缺的不是又一个大而全的数据平台，而是一个**轻量、现代、易于扩展**的数据 IDE——开箱够用，不够的地方可以自己插件化扩展，而不是一上来就给你塞一堆用不上的东西。

### 技术变迁带来的痛苦

回看过去几年大数据团队经历的技术变迁：

1. 最早大家买云服务器，手动搭建 Hadoop/YARN 集群
2. 慢慢地开始使用云厂商提供的 EMR
3. 因为各种原因从一家云厂商迁移到另一家云厂商
4. 最终因为成本和运维压力，又开始将任务迁向 Serverless 服务

每一次技术切换，都意味着数据平台的功能要改来改去，代码越来越割裂，技术债越积越多。

### 新一代数据平台应该是什么样的

我们需要的是一个**可以在任何云上都能适配的数据平台**。为此 Rudder 设计了 **Runtime 模块**——通过 SPI 插件化的执行环境抽象层，让上层的任务调度、工作流编排与底层的执行引擎彻底解耦。无论你的集群跑在 AWS EMR、阿里云 Dataproc 还是 Serverless Spark 上，只需要适配对应的 Runtime 插件，上层平台代码无需改动。

### 流批一体

传统的数据平台往往将批处理和流处理割裂为两套系统、两套开发模式、两套运维体系。Rudder 在设计之初就通过统一的 TaskChannel SPI 将流和批纳入同一个平台：

- **批任务**（Hive / Spark / SQL）— 通过 DAG 工作流编排和 Cron 调度
- **流任务**（Flink）— 在同一个 IDE 中开发和提交，通过独立的生命周期管理进行启停和监控

开发者在一个平台上完成所有数据任务的开发、管理和运维，不再需要在多套系统之间来回切换。

### 关于名字

Rudder，舵。希望每一位使用这个平台的人，都能在数据的海洋中自由掌舵。

---

## 核心特性

- **在线 IDE** — Monaco Editor 编辑 SQL / Python / Shell，即时执行并查看结果
- **工作流编排** — AntV X6 可视化 DAG 编辑器，支持条件分支 / Switch / 子工作流 / 依赖节点，内置 Cron 调度
- **多引擎支持** — MySQL、Hive、StarRocks、Trino、Spark、Flink、Python、Shell、SeaTunnel
- **流批一体** — Flink SQL / JAR 原生支持 STREAMING 模式，与批任务统一管理
- **Runtime 插件化** — SPI 抽象执行环境，已适配本地集群 / 阿里云（Ververica + EMR Serverless）/ AWS
- **弹性架构** — Server / Execution 分离，自研 RPC 通信，支持多实例水平扩展
- **轻量依赖** — 服务注册基于 MySQL，无 ZooKeeper / Redis 强依赖
- **AI 辅助** — Claude / OpenAI 驱动的 Text2SQL、SQL 解释 / 优化 / 诊断、Agent 模式（Tool Calling）
- **元数据管理** — DataHub GraphQL 集成 + JDBC 兜底，IDE 内置表结构浏览
- **权限体系** — 平台管理（Admin）+ 工作空间管理（Owner）两层权限，RBAC 四级角色，数据源 Workspace 级授权，用户可同时属于多个 Workspace
- **SSO 登录** — 支持 OAuth2 / OIDC / LDAP 企业统一登录
- **发布审批** — 飞书 / Slack / KissFlow 审批渠道（SPI 可扩展）
- **调度对接** — 内置 Cron 调度，支持对接 DolphinScheduler 进行生产级调度发布
- **版本管理** — 脚本 / 工作流版本快照 + Diff 对比
- **审计日志** — 全操作审计追溯（用户 / 模块 / 动作 / IP）
- **多格式导出** — 查询结果支持 JSON / CSV / Parquet / ORC / Avro
- **多存储后端** — 文件存储支持本地 / HDFS / 阿里云 OSS / AWS S3
- **国际化** — 完整中英双语

## 技术栈

| 层 | 技术 |
|:---|:-----|
| 后端 | Java 21、Spring Boot 3.2.5、MyBatis-Plus 3.5.6 |
| 前端 | Vue 3、TypeScript、Element Plus、Monaco Editor、AntV X6 |
| 通信 | 自研 RPC 框架（Netty） |
| 数据库 | MySQL 8.x（主库 + 服务注册） |
| 大数据 | Hive、StarRocks、Trino、Spark、Flink、SeaTunnel、HDFS |
| 云平台 | 阿里云（Ververica Flink + EMR Serverless Spark）、AWS（EMR / Glue / SageMaker） |
| AI | Claude API、OpenAI API |
| 可选 | Redis（仅元数据缓存） |

## 架构

```
Server (rudder-api:5680)                 Execution (rudder-execution:5681)
├ REST API + 前端静态资源                  ├ RPC 接收任务执行请求
├ 创建 TaskInstance                       ├ TaskPipeline 执行管线
├ RPC 派发任务到 Execution                 │  ├ ResourceResolver（资源依赖解析）
├ 日志/结果查询转发                         │  ├ RuntimeInjector（运行时注入）
├ 服务注册 (type=SERVER)                   │  ├ JdbcResourceInjector（JDBC 资源注入）
├ 心跳 10s                                │  └ ResultCollector（结果收集）
│                                         ├ 日志写本地文件 + DB
│                                         ├ 服务注册 (type=EXECUTION)
│                                         └ 心跳 10s

               ↕  MySQL（唯一存储）
          service_registry — 服务注册 + 心跳
          task_instance    — 统一任务实例
```

- 支持多 Server / 多 Execution 实例水平扩展
- 服务注册到 MySQL，心跳维持，超时自动下线
- 不依赖 ZooKeeper、Redis 等中间件

## 模块结构

```
rudder/
├── rudder-common                 通用：响应体、异常、审计、工具类
├── rudder-dao                    数据访问：Entity / Mapper / DAO / Enum（21 张表）
├── rudder-service/
│   ├── rudder-rpc                    自研 RPC 框架（Netty, 序列化, 方法注册）
│   ├── rudder-result/                结果序列化 SPI
│   │   ├── rudder-result-api             结果格式接口
│   │   ├── rudder-result-json            JSON 格式
│   │   ├── rudder-result-csv             CSV 格式
│   │   ├── rudder-result-parquet         Parquet 格式
│   │   ├── rudder-result-orc             ORC 格式
│   │   └── rudder-result-avro            Avro 格式
│   ├── rudder-workspace              Workspace / Project / User / RBAC
│   ├── rudder-datasource             全局数据源管理 + AES 凭证加密 + 连接池
│   ├── rudder-script                 脚本 CRUD + 任务派发 + 服务注册
│   ├── rudder-workflow               DAG 编排 + 调度 + 实例 + 变量池 + 发布审批
│   ├── rudder-notification           通知服务（owl-server 集成）
│   └── rudder-version/               版本管理
│       ├── rudder-version-core           版本 SPI
│       └── rudder-version-db             MySQL 实现
├── rudder-task/
│   ├── rudder-task-api               任务 SPI 接口、TaskType 枚举（15 种任务类型）
│   ├── rudder-task-mysql              MySQL SQL
│   ├── rudder-task-hive               Hive SQL
│   ├── rudder-task-starrocks          StarRocks SQL
│   ├── rudder-task-trino              Trino SQL
│   ├── rudder-task-spark              Spark SQL + JAR
│   ├── rudder-task-flink              Flink SQL + JAR（批 + 流）
│   ├── rudder-task-python             Python 脚本
│   ├── rudder-task-shell              Shell 脚本
│   ├── rudder-task-seatunnel          SeaTunnel 数据集成
│   └── rudder-task-controlflow        控制流（条件 / Switch / 子工作流 / 依赖）
├── rudder-runtime/
│   ├── rudder-runtime-api             执行环境 SPI（Spark/Flink SQL+JAR）
│   ├── rudder-runtime-cluster         本地集群运行时
│   ├── rudder-runtime-aliyun          阿里云（Ververica Flink + EMR Serverless Spark）
│   └── rudder-runtime-aws             AWS（EMR / Glue / SageMaker）
├── rudder-metadata/
│   ├── rudder-metadata-core           元数据接口 + 缓存
│   ├── rudder-metadata-jdbc           JDBC 元数据采集（兜底）
│   └── rudder-metadata-datahub        DataHub GraphQL 对接
├── rudder-ai/
│   ├── rudder-ai-core                 AI 接口抽象（chat / complete / tools）
│   ├── rudder-ai-claude               Claude 实现
│   └── rudder-ai-openai               OpenAI 实现
├── rudder-file/
│   ├── rudder-file-core               文件存储 SPI
│   ├── rudder-file-local              本地文件系统
│   ├── rudder-file-hdfs               HDFS
│   ├── rudder-file-oss                阿里云 OSS
│   └── rudder-file-s3                 AWS S3
├── rudder-approval/
│   ├── rudder-approval-core           审批 SPI
│   ├── rudder-approval-feishu         飞书审批
│   ├── rudder-approval-slack          Slack 审批
│   └── rudder-approval-kissflow       KissFlow 审批
├── rudder-api                     Controller + JWT（Server 入口，端口 5680）
├── rudder-execution               TaskPipeline + 日志管理（Execution 入口，端口 5681）
├── rudder-ui                      Vue 3 前端
├── rudder-dist                    打包 & Docker 构建
└── deploy/docker/                 Docker Compose 部署文件
```

## SPI 插件体系

Rudder 全面采用 Java SPI + @AutoService 实现可插拔扩展，新增引擎或平台只需实现对应接口 + 一个注解：

| SPI 接口 | 当前实现 | 说明 |
|:---------|:---------|:-----|
| TaskChannel | 10 | 任务执行引擎（MySQL / Hive / StarRocks / Trino / Spark / Flink / Python / Shell / SeaTunnel / ControlFlow） |
| EngineRuntime | 3 | 执行环境（本地集群 / 阿里云 / AWS） |
| MetadataClient | 2 | 元数据提供者（JDBC / DataHub） |
| AiClient | 2 | AI 提供商（Claude / OpenAI） |
| FileStorage | 4 | 文件存储（本地 / HDFS / OSS / S3） |
| ApprovalNotifier | 3 | 审批渠道（飞书 / Slack / KissFlow） |
| ResultFormat | 5 | 结果序列化（JSON / CSV / Parquet / ORC / Avro） |
| VersionStore | 1 | 版本持久化（MySQL） |

## 任务类型

| 类型 | TaskType | 执行模式 |
|:-----|:---------|:---------|
| SQL | HIVE_SQL, STARROCKS_SQL, MYSQL_SQL, TRINO_SQL, SPARK_SQL, FLINK_SQL | BATCH（Flink 支持 STREAMING） |
| JAR | SPARK_JAR, FLINK_JAR | BATCH（Flink 支持 STREAMING） |
| 脚本 | PYTHON, SHELL | BATCH |
| 数据集成 | SEATUNNEL | BATCH |
| 控制流 | CONDITION, SWITCH, SUB_WORKFLOW, DEPENDENT | Server 端执行 |

## 快速开始

### 方式一：Docker 一键部署

项目自带 Docker Compose 文件，包含 MySQL、StarRocks、HDFS、Hive、Trino 等基础服务，可以一键拉起完整的开发/测试环境。

> **注意**：自带的基础服务均为单节点部署，仅适用于开发和功能测试。生产环境请自行搭建或使用云厂商提供的大数据服务，Rudder 只需配置对应的连接信息即可接入。

```bash
# 克隆项目
git clone <repo-url> && cd rudder

# 准备配置
cp .env.example .env

# 全量部署（基础服务 + 编译 + 启动应用）
bash deploy/docker/redeploy.sh
```

部署完成后访问 **http://localhost:5680** 即可使用。

### 方式二：本地开发

```bash
# 准备配置
cp .env.example .env
```

**1. 启动基础服务**

```bash
docker compose -f deploy/docker/local-development.docker-compose.yml up -d
```

**2. 编译后端**

```bash
./mvnw clean package -DskipTests
```

**3. 启动 API Server（5680）**

```bash
export $(grep -v '^#' .env | xargs)
java -jar rudder-api/target/rudder-api-*.jar
```

**4. 启动 Execution Worker（5681）**

```bash
export $(grep -v '^#' .env | xargs)
java -jar rudder-execution/target/rudder-execution-*.jar
```

**5. 启动前端开发服务器**

```bash
cd rudder-ui && npm install && npm run dev
```

## 部署命令

`deploy/docker/redeploy.sh` 是全量部署脚本，执行流程：

| 步骤 | 操作 |
|:-----|:-----|
| 1 | 停止应用容器 |
| 2 | 停止基础服务（含数据清理） |
| 3 | Maven 编译打包 |
| 4 | 启动基础服务 |
| 5 | 构建 Docker 镜像并启动应用 |

脚本会读取项目根目录 `.env`，未提供时会直接退出。

**可选参数：**

```bash
# 仅重新部署应用（保留基础服务和数据）
bash deploy/docker/redeploy.sh --skip-infra

# 跳过编译，快速重启应用容器
bash deploy/docker/redeploy.sh --skip-infra --skip-build
```

## 依赖服务

| 服务 | 用途 | 必需 |
|:-----|:-----|:----:|
| MySQL 8.x | 主数据库 + 服务注册 | ✓ |
| Hive | 离线数据仓库 | |
| StarRocks | OLAP 分析引擎 | |
| Trino | 联邦查询引擎 | |
| HDFS | 分布式存储 | |
| Redis | 元数据缓存 | |

> 除 MySQL 外，其余服务按需接入。Rudder 通过数据源管理动态配置，不依赖特定组件。

## 项目截图

> TODO: 待补充

## 参与贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/your-feature`)
3. 提交更改 (`git commit -m 'Add some feature'`)
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

## 联系

- GitHub Issues: [https://github.com/zzih/rudder/issues](https://github.com/zzih/rudder/issues)

## License

[Apache License 2.0](LICENSE)
