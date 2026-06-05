#!/bin/bash
# TirTir AI Service — Mac/Linux Startup Script
# Tự động giải phóng port 8000 rồi chạy uvicorn

PORT=8000

echo "🔍 Checking port $PORT..."
PIDS=$(lsof -ti:$PORT 2>/dev/null)

if [ -n "$PIDS" ]; then
    echo "⚠️  Killing processes on port $PORT: $PIDS"
    echo "$PIDS" | xargs kill -9
    sleep 1
else
    echo "✅ Port $PORT is free."
fi

echo "🚀 Starting AI Service on port $PORT..."
python3 -m uvicorn main:app --host 0.0.0.0 --port $PORT --reload
