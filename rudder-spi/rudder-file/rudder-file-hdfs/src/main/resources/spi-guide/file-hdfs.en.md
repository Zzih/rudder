---
description: Hadoop HDFS distributed file storage
---

## HDFS File Storage

Use Hadoop HDFS as distributed file storage.

### Prerequisites
1. Deploy an HDFS cluster (NameNode + DataNode)
2. Make sure Rudder service nodes can reach HDFS

### Configuration
- **NameNode URL**: HDFS filesystem address, e.g. `hdfs://namenode:8020`
- **Base Path**: root directory for stored files, default `/rudder`

### When to choose this
- Multi-node deployments that need shared file storage
- Existing Hadoop ecosystem
