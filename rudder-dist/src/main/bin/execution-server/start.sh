#!/bin/bash
# Start Rudder Execution Server
BIN_DIR=$(cd "$(dirname "$0")/../.."; pwd)/bin
source "$BIN_DIR/env.sh"
exec "$BIN_DIR/start-server.sh" \
    "execution-server" \
    "io.github.zzih.rudder.execution.RudderExecutionApplication" \
    "$EXECUTION_SERVER_HEAP" \
    "-Drudder.execution.log-dir=$RUDDER_HOME/logs/tasks"
