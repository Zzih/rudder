---
description: 自建集群,worker 本地通过 CLI 提交集群任务
---

## Local / 自建集群 Runtime

在 worker 节点本地通过 CLI 工具（`spark-submit` / `flink` / 其它引擎 CLI）提交集群任务，适用于自建 Hadoop/YARN/K8s 集群。
SQL 任务通过 JDBC 连接对应的 Thrift Server 或 SQL Gateway 执行（URL 由数据源配置决定）。

### 参数
- **workDir**：生成临时 shell 脚本的目录，需 worker 进程可写；留空用 `/tmp/rudder/tasks`
- **环境变量脚本**：任意 shell 片段，在执行引擎 CLI 前 source。用它来 `export SPARK_HOME / FLINK_HOME / PATH` 或
  `source /etc/profile.d/xxx.sh` 等，**一个文本框覆盖所有引擎**——未来新增 Ray、Trino 等集群任务也无需改动这里的 schema

### 脚本执行流程
Rudder 生成临时 `rudder_*.sh` 文件，用 `bash -l` 登录 shell 执行：
1. `bash -l` 自动加载 `/etc/profile` + `~/.profile`
2. 再 source 上面填的环境变量脚本（如果有）
3. 最后调用引擎 CLI（`spark-submit` / `flink` / …），从 PATH 解析

### 环境变量脚本模板
如 `~/.profile` 已经设好 PATH 可以整段留空。需要显式 override 时参考：

```bash
# 引擎根目录（按需）
export SPARK_HOME=/opt/bigdata/spark
export FLINK_HOME=/opt/bigdata/flink
export HADOOP_HOME=/opt/bigdata/hadoop

# 把引擎 bin 追加到 PATH
export PATH=$SPARK_HOME/bin:$FLINK_HOME/bin:$HADOOP_HOME/bin:$PATH

# 或直接 source 集群统一环境脚本
# source /etc/profile.d/bigdata.sh
```
