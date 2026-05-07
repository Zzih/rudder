#!/bin/bash
# Common server startup logic
# Usage: start-server.sh <server-name> <main-class> <heap-opts> [extra-java-opts...]
set -e

SERVER_NAME="$1"
MAIN_CLASS="$2"
HEAP_OPTS="$3"
shift 3
EXTRA_OPTS="$*"

BIN_DIR=$(cd "$(dirname "$0")"; pwd)
RUDDER_HOME=$(cd "$BIN_DIR/.."; pwd)
SERVER_HOME="$RUDDER_HOME/$SERVER_NAME"
CONF_DIR="$SERVER_HOME/conf"
source "$BIN_DIR/env.sh"

LOG_DIR="$SERVER_HOME/logs"
PID_FILE="$RUDDER_PID_DIR/${SERVER_NAME}.pid"

mkdir -p "$LOG_DIR"

CLASSPATH="$CONF_DIR:$SERVER_HOME/libs/*"

JAVA_OPTS="$HEAP_OPTS $RUDDER_JVM_OPTS"
JAVA_OPTS="$JAVA_OPTS -Dspring.config.additional-location=file:$CONF_DIR/"
JAVA_OPTS="$JAVA_OPTS -Dlogging.file.path=$LOG_DIR"
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=$LOG_DIR/${SERVER_NAME}-heapdump.hprof"
[ -n "$EXTRA_OPTS" ] && JAVA_OPTS="$JAVA_OPTS $EXTRA_OPTS"

echo "Starting $SERVER_NAME ..."
echo "  JAVA_HOME: $JAVA_HOME"
echo "  JAVA_OPTS: $JAVA_OPTS"

# Set working directory to RUDDER_HOME so file:ui/ resolves correctly
cd "$RUDDER_HOME"

# Foreground mode: Docker (PID 1) or explicit RUDDER_FOREGROUND=true
if [ "$$" = "1" ] || [ "${RUDDER_FOREGROUND}" = "true" ]; then
    exec "$JAVA_HOME/bin/java" $JAVA_OPTS -cp "$CLASSPATH" "$MAIN_CLASS"
fi

# Background mode: traditional deployment
nohup "$JAVA_HOME/bin/java" $JAVA_OPTS -cp "$CLASSPATH" "$MAIN_CLASS" \
    > "$LOG_DIR/${SERVER_NAME}.out" 2>&1 &

echo $! > "$PID_FILE"
echo "$SERVER_NAME started (pid=$(cat "$PID_FILE"))"
