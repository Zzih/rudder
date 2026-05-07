---
description: Amazon S3 对象存储
---

## Amazon S3 文件存储

使用 Amazon S3 作为文件存储后端。

### 前置条件
1. 开通 [Amazon S3](https://aws.amazon.com/s3/) 服务
2. 创建 S3 Bucket（建议与 Rudder 服务同 Region）
3. 获取 IAM 凭证

### 获取 Access Key
1. 登录 [IAM Console](https://console.aws.amazon.com/iam/)
2. 创建 IAM 用户并附加 `AmazonS3FullAccess` 策略
3. 在 Security credentials 中创建 Access Key

### Region 格式
- 美东：`us-east-1`
- 亚太（新加坡）：`ap-southeast-1`
- 亚太（东京）：`ap-northeast-1`
