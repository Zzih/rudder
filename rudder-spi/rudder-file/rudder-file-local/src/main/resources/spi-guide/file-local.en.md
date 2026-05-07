---
description: Local disk storage (no config, single-node)
---

## Local File Storage (default)

Files are written directly to the server's local disk. **No extra configuration required.**

### Characteristics
- Zero dependencies: no extra storage service
- Low latency: direct local disk reads/writes
- Suited for single-node deployments and dev/test environments

### Caveats
- In multi-node deployments, files are not shared between nodes
- Use HDFS / OSS / S3 in production
