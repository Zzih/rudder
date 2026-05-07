#!/bin/bash
# Rudder global environment configuration

# Auto-detect RUDDER_HOME
RUDDER_HOME="${RUDDER_HOME:-$(cd "$(dirname "$0")/.."; pwd)}"
export RUDDER_HOME

# Load .env if present (before defaults, so .env values take precedence)
[ -f "$RUDDER_HOME/.env" ] && set -a && . "$RUDDER_HOME/.env" && set +a

# Java (require Java 21)
if command -v update-alternatives &> /dev/null; then
  JAVA21_PATH=$(update-alternatives --list java 2>/dev/null | grep 21 | head -1)
  if [ -n "$JAVA21_PATH" ]; then
    export JAVA_HOME="$(dirname $(dirname $JAVA21_PATH))"
  fi
fi
export JAVA_HOME="${JAVA_HOME:?JAVA_HOME is not set and Java 21 was not found}"

# Database (override in production)
export RUDDER_DB_URL="${RUDDER_DB_URL:-jdbc:mysql://127.0.0.1:3306/rudder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true}"
export RUDDER_DB_USERNAME="${RUDDER_DB_USERNAME:-rudder}"
export RUDDER_DB_PASSWORD="${RUDDER_DB_PASSWORD:-rudder123}"

# JVM settings per service
export API_SERVER_HEAP="${API_SERVER_HEAP:--Xms512m -Xmx1024m}"
export EXECUTION_SERVER_HEAP="${EXECUTION_SERVER_HEAP:--Xms256m -Xmx512m}"

# Common JVM options
export RUDDER_JVM_OPTS="-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError"

# Directories
export RUDDER_LOG_DIR="${RUDDER_LOG_DIR:-$RUDDER_HOME/logs}"
export RUDDER_PID_DIR="${RUDDER_PID_DIR:-$RUDDER_HOME/pid}"

mkdir -p "$RUDDER_LOG_DIR" "$RUDDER_PID_DIR"
