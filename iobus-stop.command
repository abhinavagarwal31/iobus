#!/bin/bash
# Double-click this file to stop the running iOBus server.

PIDFILE="/tmp/iobus-server.pid"

# Wait, then close this Terminal window (matched by tty, so only this window closes)
close_window() {
    echo ""
    echo "This window will close in 10 seconds."
    sleep 10
    osascript -e 'tell application "Terminal" to close (every window whose tty is "'"$(tty)"'")' >/dev/null 2>&1 &
}

if [ ! -f "$PIDFILE" ]; then
    echo "No PID file found — server doesn't appear to be running."
    close_window
    exit 0
fi

PID=$(cat "$PIDFILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo "Server (PID $PID) isn't running. Cleaning up stale PID file."
    rm -f "$PIDFILE"
    close_window
    exit 0
fi

echo "Stopping iOBus server (PID $PID)..."
kill "$PID"

# Wait up to 5s for a graceful shutdown before giving up
for _ in 1 2 3 4 5; do
    if ! kill -0 "$PID" 2>/dev/null; then
        break
    fi
    sleep 1
done

if kill -0 "$PID" 2>/dev/null; then
    echo "Server did not stop gracefully after 5s (still PID $PID)."
    echo "If it's stuck, run:  kill -9 $PID"
else
    echo "Server stopped."
    rm -f "$PIDFILE"
fi

close_window
