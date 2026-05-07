---
description: 阿里云对象存储 OSS
---

## 阿里云 OSS 文件存储

使用阿里云对象存储服务（OSS）作为文件存储后端。

### 前置条件
1. 开通 [阿里云 OSS](https://www.aliyun.com/product/oss) 服务
2. 创建 Bucket（建议与 Rudder 服务同 Region）
3. 获取 AccessKey（推荐使用 RAM 子账号）

### 获取 AccessKey
1. 登录 [RAM 控制台](https://ram.console.aliyun.com/)
2. 创建用户并授予 `AliyunOSSFullAccess` 权限
3. 在用户详情页创建 AccessKey

### Endpoint 格式
- 内网：`https://oss-cn-hangzhou-internal.aliyuncs.com`（同 Region 免流量费）
- 外网：`https://oss-cn-hangzhou.aliyuncs.com`
