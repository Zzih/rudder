#!/bin/bash
# Rudder unified daemon control
# Usage: rudder-daemon.sh {start|stop|status|restart} {api-server|execution-server|all}
set -e

BIN_DIR=$(cd "$(dirname "$0")"; pwd)
source "$BIN_DIR/env.sh"

usage() {
    echo "Usage: $0 {start|stop|status|restart} {api-server|execution-server|all}"
    exit 1
}

[ $# -lt 2 ] && usage

ACTION="$1"
SERVER="$2"

SERVERS=()
if [ "$SERVER" = "all" ]; then
    SERVERS=("api-server" "execution-server")
else
    SERVERS=("$SERVER")
fi

start_server() {
    local name="$1"
    local start_script="$RUDDER_HOME/$name/bin/start.sh"
    local pid_file="$RUDDER_PID_DIR/${name}.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$name is already running (pid=$pid)"
            return 0
        fi
        rm -f "$pid_file"
    fi

    if [ ! -f "$start_script" ]; then
        echo "ERROR: $start_script not found"
        return 1
    fi

    bash "$start_script"
}

stop_server() {
    local name="$1"
    local pid_file="$RUDDER_PID_DIR/${name}.pid"

    if [ ! -f "$pid_file" ]; then
        echo "$name is not running (no pid file)"
        return 0
    fi

    local pid=$(cat "$pid_file")
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "$name is not running (stale pid file)"
        rm -f "$pid_file"
        return 0
    fi

    echo "Stopping $name (pid=$pid) ..."
    kill "$pid"

    # Wait up to 30 seconds for graceful shutdown
    for ((i=1; i<=30; i++)); do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "$name stopped"
            rm -f "$pid_file"
            return 0
        fi
        sleep 1
    done

    echo "Force killing $name (pid=$pid) ..."
    kill -9 "$pid" 2>/dev/null
    rm -f "$pid_file"
    echo "$name killed"
}

status_server() {
    local name="$1"
    local pid_file="$RUDDER_PID_DIR/${name}.pid"

    if [ ! -f "$pid_file" ]; then
        echo "$name: NOT RUNNING"
        return 1
    fi

    local pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
        echo "$name: RUNNING (pid=$pid)"
        return 0
    else
        echo "$name: NOT RUNNING (stale pid)"
        rm -f "$pid_file"
        return 1
    fi
}

for srv in "${SERVERS[@]}"; do
    case "$ACTION" in
        start)   start_server "$srv" ;;
        stop)    stop_server "$srv" ;;
        status)  status_server "$srv" ;;
        restart) stop_server "$srv"; start_server "$srv" ;;
        *)       usage ;;
    esac
done
