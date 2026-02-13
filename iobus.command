#!/bin/bash
# Double-click this file to start the iOBus server in the background.
# A log is written to /tmp/iobus-server.log

DIR="$(cd "$(dirname "$0")" && pwd)"
PIDFILE="/tmp/iobus-server.pid"
LOG="/tmp/iobus-server.log"

# Kill any previously running instance
if [ -f "$PIDFILE" ]; then
    OLD_PID=$(cat "$PIDFILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping previous server (PID $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null
        sleep 1
    fi
    rm -f "$PIDFILE"
fi

cd "$DIR"

# Get local IP address
IP_ADDR=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "unknown")

echo "Starting iOBus server..."
nohup "$DIR/.venv/bin/python" -m server >> "$LOG" 2>&1 &
echo $! > "$PIDFILE"
echo "Server started (PID $(cat "$PIDFILE")). Logs: $LOG"
echo "IP Address: $IP_ADDR"
echo ""
echo "To stop later, run:  kill \$(cat $PIDFILE)"
echo ""
echo "This window will close in 3 seconds."
sleep 3
