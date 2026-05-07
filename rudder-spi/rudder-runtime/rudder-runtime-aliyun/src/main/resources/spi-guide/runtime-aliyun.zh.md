---
description: 阿里云 EMR Serverless Spark + 实时计算 Flink 版(VVP)
---

## 阿里云 Runtime (EMR Serverless Spark + VVP Flink)

Spark 作业提交到 **EMR Serverless Spark**，Flink 作业提交到 **实时计算 Flink 版（VVP）**。

### 前置条件
1. 开通 EMR Serverless Spark / 实时计算 Flink 版
2. 在 RAM 创建子账号并授予相应权限，生成 AccessKey
3. 分别在控制台创建 Spark / Flink workspace，获取 workspace ID

### 配置模板
将下面内容粘贴到左侧配置框并替换占位值（`#` 开头为注释）：

```properties
# 凭证（必填）
accessKeyId=LTAI5t...
accessKeySecret=your-secret

# 地域，影响 SDK endpoint（必填）
regionId=cn-hangzhou

# Serverless Spark 工作空间
spark.workspaceId=w-xxxxxxx
spark.resourceQueueId=rq-xxxxxxx

# VVP Flink 工作空间
flink.workspaceId=w-xxxxxxx
flink.namespace=default
```

未出现在上表的新键（未来 VVP / Spark 新特性）直接按同样 `xx.yy=value` 格式追加即可。
