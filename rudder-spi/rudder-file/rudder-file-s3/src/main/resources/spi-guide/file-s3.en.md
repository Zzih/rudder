---
description: Amazon S3 object storage
---

## Amazon S3 File Storage

Use Amazon S3 as the file storage backend.

### Prerequisites
1. Activate [Amazon S3](https://aws.amazon.com/s3/)
2. Create an S3 Bucket (same Region as Rudder is recommended)
3. Obtain IAM credentials

### Get an Access Key
1. Sign in to the [IAM Console](https://console.aws.amazon.com/iam/)
2. Create an IAM user and attach the `AmazonS3FullAccess` policy
3. Create an Access Key under Security credentials

### Region format
- US East: `us-east-1`
- Asia Pacific (Singapore): `ap-southeast-1`
- Asia Pacific (Tokyo): `ap-northeast-1`
