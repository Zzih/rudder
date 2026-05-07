---
description: Aliyun Object Storage Service (OSS)
---

## Aliyun OSS File Storage

Use Aliyun OSS as the file storage backend.

### Prerequisites
1. Activate the [Aliyun OSS](https://www.aliyun.com/product/oss) service
2. Create a Bucket (in the same Region as Rudder is recommended)
3. Get an AccessKey (a RAM sub-account is recommended)

### Get an AccessKey
1. Sign in to the [RAM console](https://ram.console.aliyun.com/)
2. Create a user and grant `AliyunOSSFullAccess`
3. Create an AccessKey on the user detail page

### Endpoint format
- Internal: `https://oss-cn-hangzhou-internal.aliyuncs.com` (no traffic fee within the same Region)
- Public: `https://oss-cn-hangzhou.aliyuncs.com`
