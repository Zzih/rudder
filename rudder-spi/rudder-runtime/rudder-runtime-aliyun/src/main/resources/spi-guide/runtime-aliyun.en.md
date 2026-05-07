---
description: Aliyun EMR Serverless Spark + Realtime Compute Flink (VVP)
---

## Aliyun Runtime (EMR Serverless Spark + VVP Flink)

Spark jobs are submitted to **EMR Serverless Spark**; Flink jobs are submitted to **Realtime Compute for Apache Flink (VVP)**.

### Prerequisites
1. Activate EMR Serverless Spark / Realtime Compute for Apache Flink
2. Create a RAM sub-account with the right permissions and generate an AccessKey
3. Create Spark / Flink workspaces in the console and note the workspace IDs

### Configuration template
Paste the snippet into the form on the left and replace the placeholders (`#` lines are comments):

```properties
# Credentials (required)
accessKeyId=LTAI5t...
accessKeySecret=your-secret

# Region — drives the SDK endpoint (required)
regionId=cn-hangzhou

# Serverless Spark workspace
spark.workspaceId=w-xxxxxxx
spark.resourceQueueId=rq-xxxxxxx

# VVP Flink workspace
flink.workspaceId=w-xxxxxxx
flink.namespace=default
```

For new keys not listed above (future VVP / Spark features), append them with the same `xx.yy=value` style.
