---
description: AWS EMR Serverless + Amazon Managed Service for Apache Flink
---

## AWS Runtime (EMR Serverless + Managed Flink)

Spark 作业提交到 **EMR Serverless**，Flink 作业提交到 **Amazon Managed Service for Apache Flink**。

### 认证
使用 AWS 默认凭证链（环境变量 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`、`~/.aws/credentials`、IAM Role），此处不填 AK/SK。
建议 worker 使用 IAM Role。

### 前置条件
1. 在 EMR Serverless 控制台预先创建 Spark Application
2. 创建 IAM Role：Spark `ExecutionRole`、Flink `ServiceExecutionRole`
3. 为 Flink 准备 S3 bucket 用于存放代码/artifacts

### 配置模板
将下面内容粘贴到左侧配置框并替换占位值：

```properties
# 区域（必填）
region=us-east-1

# EMR Serverless Spark
spark.applicationId=00f7xxxx
spark.executionRoleArn=arn:aws:iam::xxx:role/xxx

# Amazon Managed Service for Apache Flink
flink.serviceExecutionRole=arn:aws:iam::xxx:role/xxx
flink.s3Bucket=s3://rudder-flink/
flink.runtimeEnvironment=FLINK-1_18
```

未出现在上表的新键直接按同样 `xx.yy=value` 格式追加即可。
