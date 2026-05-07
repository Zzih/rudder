#!/bin/bash
# Start Rudder API Server
BIN_DIR=$(cd "$(dirname "$0")/../.."; pwd)/bin
exec "$BIN_DIR/start-server.sh" \
    "api-server" \
    "io.github.zzih.rudder.api.RudderApiApplication" \
    "$API_SERVER_HEAP"
