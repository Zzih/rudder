---
description: 默认 Runtime,集中下发 Worker 本机子进程的环境变量
---

## Local Runtime

默认 Runtime,不接管任何 TaskType。唯一职责:把本页配置的环境变量下发到 Worker 本机起子进程的任务。

### 哪些任务消费 envVars

凡是在 Worker 本机起子进程的任务,都会消费本页 envVars:

- **Shell** —— `bash -c <script>`
- **Python** —— `python3 <script>`
- **SeaTunnel** —— `seatunnel.sh --config ...`
- **Spark JAR** —— `spark-submit ...`(本机 client 提交到 YARN/standalone)
- **Flink JAR** —— `flink run ...`(本机 client 提交到 YARN/standalone)

注入路径:`ProcessBuilder.environment().putAll(envVars)`,merge 到父进程之上,**同名 key 覆盖父进程值**。

### 不消费 envVars 的任务

走 JDBC / 内嵌 Java 客户端的任务不起子进程,本页对其无效:Hive / MySQL / PostgreSQL / Trino / ClickHouse / Doris / StarRocks / HTTP / SQL 类任务。

云端 Runtime(AWS / Aliyun)接管 Spark/Flink 后走云 SDK 提交,也不起本机子进程。

### 配置格式

一行一个 `KEY=VALUE`,空行与 `#` 注释行忽略,VALUE 内可引用 `$VAR`。

```
JAVA_HOME=/opt/jdk-21
SPARK_HOME=/opt/bigdata/spark
FLINK_HOME=/opt/bigdata/flink
HADOOP_CONF_DIR=/etc/hadoop/conf
PATH=$SPARK_HOME/bin:$FLINK_HOME/bin:$PATH
```

Spark/Flink JAR 任务的 client 子进程通过 `bash -l` 启动,会先 source `/etc/profile` 与 `~/.profile`;本页 envVars 在子进程 environment 层注入,优先级高于 login shell。
