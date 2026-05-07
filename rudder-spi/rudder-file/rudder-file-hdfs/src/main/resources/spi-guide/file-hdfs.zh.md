---
description: Hadoop HDFS 分布式文件存储
---

## HDFS 文件存储

使用 Hadoop HDFS 作为分布式文件存储。

### 前置条件
1. 部署 HDFS 集群（NameNode + DataNode）
2. 确保 Rudder 服务节点可访问 HDFS

### 配置说明
- **NameNode URL**：HDFS 文件系统地址，如 `hdfs://namenode:8020`
- **Base Path**：文件存储的根目录，默认 `/rudder`

### 适用场景
- 多节点部署，需要共享文件存储
- 已有 Hadoop 生态环境
