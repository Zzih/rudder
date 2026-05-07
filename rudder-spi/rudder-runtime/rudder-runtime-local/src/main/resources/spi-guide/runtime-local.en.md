---
description: Self-managed cluster, worker submits via local CLI
---

## Local / Self-managed Cluster Runtime

The worker submits cluster jobs through local CLIs (`spark-submit` / `flink` / other engine CLIs). Use this for self-managed Hadoop/YARN/K8s clusters.
SQL tasks run via JDBC against the corresponding Thrift Server / SQL Gateway (URL is taken from the datasource configuration).

### Parameters
- **workDir**: directory where temporary shell scripts are generated; must be writable by the worker. Leave blank to use `/tmp/rudder/tasks`
- **Environment script**: a free-form shell snippet that is sourced before invoking the engine CLI. Use it to `export SPARK_HOME / FLINK_HOME / PATH` or
  `source /etc/profile.d/xxx.sh`, etc. **One textbox covers every engine** — adding Ray, Trino, etc. later requires no schema change here.

### Script execution flow
Rudder writes a temporary `rudder_*.sh` and runs it with `bash -l` (login shell):
1. `bash -l` automatically loads `/etc/profile` + `~/.profile`
2. Then sources the environment script you set above (if any)
3. Finally invokes the engine CLI (`spark-submit` / `flink` / …), resolved from PATH

### Environment script template
If your `~/.profile` already sets PATH correctly, you can leave it empty. Otherwise:

```bash
# Engine roots (as needed)
export SPARK_HOME=/opt/bigdata/spark
export FLINK_HOME=/opt/bigdata/flink
export HADOOP_HOME=/opt/bigdata/hadoop

# Append engine bin to PATH
export PATH=$SPARK_HOME/bin:$FLINK_HOME/bin:$HADOOP_HOME/bin:$PATH

# Or source a unified cluster env script
# source /etc/profile.d/bigdata.sh
```
