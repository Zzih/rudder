---
description: AWS EMR Serverless + Amazon Managed Service for Apache Flink
---

## AWS Runtime (EMR Serverless + Managed Flink)

Spark jobs are submitted to **EMR Serverless**; Flink jobs are submitted to **Amazon Managed Service for Apache Flink**.

### Authentication
Uses the AWS default credentials chain (env vars `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`, `~/.aws/credentials`, or IAM Role). Don't put AK/SK here.
An IAM Role on the worker is recommended.

### Prerequisites
1. Create the Spark Application in EMR Serverless beforehand
2. Create IAM Roles: Spark `ExecutionRole`, Flink `ServiceExecutionRole`
3. Provision an S3 bucket for Flink code/artifacts

### Configuration template
Paste the snippet into the form on the left and replace the placeholders:

```properties
# Region (required)
region=us-east-1

# EMR Serverless Spark
spark.applicationId=00f7xxxx
spark.executionRoleArn=arn:aws:iam::xxx:role/xxx

# Amazon Managed Service for Apache Flink
flink.serviceExecutionRole=arn:aws:iam::xxx:role/xxx
flink.s3Bucket=s3://rudder-flink/
flink.runtimeEnvironment=FLINK-1_18
```

For new keys not listed above, append them with the same `xx.yy=value` style.
