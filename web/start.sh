#!/bin/bash
# Serves the web app on http://localhost:8080

PORT=8080
echo "Starting NYC Commute Optimizer..."
echo "Open http://localhost:$PORT in your browser"
echo "Configure your locations in Settings -> http://localhost:$PORT/settings.html"
echo ""

# Try Python first (most common), then Node
if command -v python3 &> /dev/null; then
    open "http://localhost:$PORT" 2>/dev/null || xdg-open "http://localhost:$PORT" 2>/dev/null || true
    python3 -m http.server $PORT
elif command -v npx &> /dev/null; then
    open "http://localhost:$PORT" 2>/dev/null || xdg-open "http://localhost:$PORT" 2>/dev/null || true
    npx serve -l $PORT .
else
    echo "Error: Python3 or Node.js required"
    exit 1
fi
