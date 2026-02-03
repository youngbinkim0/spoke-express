#!/bin/bash
# Starts the NYC Commute Optimizer web app and Cloudflare Worker

set -e

WEB_PORT=8080
WORKER_PORT=8787
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKER_DIR="$SCRIPT_DIR/../cloudflare-worker"

# Cleanup function to kill background processes on exit
cleanup() {
    echo ""
    echo "Shutting down..."
    if [ -n "$WORKER_PID" ]; then
        kill $WORKER_PID 2>/dev/null || true
    fi
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "=== NYC Commute Optimizer ==="
echo ""

# Check for required tools
if ! command -v npx &> /dev/null; then
    echo "Error: Node.js/npx required for Cloudflare Worker"
    echo "Install from https://nodejs.org"
    exit 1
fi

# Start Cloudflare Worker in background
echo "Starting Cloudflare Worker on http://localhost:$WORKER_PORT..."
cd "$WORKER_DIR"
npx wrangler dev --port $WORKER_PORT &
WORKER_PID=$!
cd "$SCRIPT_DIR"

# Wait for worker to start
sleep 3

echo ""
echo "Starting web server on http://localhost:$WEB_PORT..."
echo ""
echo "===================================="
echo "  App:      http://localhost:$WEB_PORT"
echo "  Settings: http://localhost:$WEB_PORT/settings.html"
echo "  Worker:   http://localhost:$WORKER_PORT"
echo "===================================="
echo ""
echo "Set Worker URL in Settings to: http://localhost:$WORKER_PORT"
echo ""

# Open browser
open "http://localhost:$WEB_PORT" 2>/dev/null || xdg-open "http://localhost:$WEB_PORT" 2>/dev/null || true

# Start web server (blocking)
if command -v python3 &> /dev/null; then
    python3 -m http.server $WEB_PORT
else
    npx serve -l $WEB_PORT .
fi
