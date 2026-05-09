---
description: Default Runtime, propagates env vars to worker-local task subprocesses
---

## Local Runtime

Default Runtime; takes over no TaskType. Sole responsibility: propagate the environment variables configured here to tasks that spawn local subprocesses on the worker.

### Tasks that consume envVars

Every task that spawns a local subprocess consumes the entries below:

- **Shell** — `bash -c <script>`
- **Python** — `python3 <script>`
- **SeaTunnel** — `seatunnel.sh --config ...`
- **Spark JAR** — `spark-submit ...` (local client submitting to YARN/standalone)
- **Flink JAR** — `flink run ...` (local client submitting to YARN/standalone)

Injection path: `ProcessBuilder.environment().putAll(envVars)` — merged on top of the parent process; **same-named keys override the parent value**.

### Tasks that do not consume envVars

Tasks running over JDBC or in-process Java clients do not spawn subprocesses and are unaffected: Hive / MySQL / PostgreSQL / Trino / ClickHouse / Doris / StarRocks / HTTP / SQL tasks.

Cloud Runtimes (AWS / Aliyun) submit Spark/Flink via cloud SDKs and likewise do not spawn local subprocesses.

### Format

One `KEY=VALUE` per line. Blank lines and lines starting with `#` are ignored. `$VAR` references inside VALUE are honoured.

```
JAVA_HOME=/opt/jdk-21
SPARK_HOME=/opt/bigdata/spark
FLINK_HOME=/opt/bigdata/flink
HADOOP_CONF_DIR=/etc/hadoop/conf
PATH=$SPARK_HOME/bin:$FLINK_HOME/bin:$PATH
```

Spark/Flink JAR client subprocesses launch via `bash -l`, which sources `/etc/profile` and `~/.profile` first; the envVars here are injected at the subprocess `environment` layer and take precedence over login-shell defaults.
