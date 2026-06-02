# TirTir AI Service — Windows Startup Script
# Tự động giải phóng port 8000 rồi chạy uvicorn

$PORT = 8000

Write-Host "🔍 Checking port $PORT..." -ForegroundColor Cyan
$pids = (netstat -ano | findstr ":$PORT") -match "LISTENING" |
    ForEach-Object { ($_ -split '\s+')[-1] } |
    Where-Object { $_ -match '^\d+$' } |
    Sort-Object -Unique

if ($pids) {
    foreach ($p in $pids) {
        Write-Host "⚠️  Killing process on port $PORT (PID $p)..." -ForegroundColor Yellow
        taskkill /PID $p /F | Out-Null
    }
    Start-Sleep -Seconds 1
} else {
    Write-Host "✅ Port $PORT is free." -ForegroundColor Green
}

Write-Host "🚀 Starting AI Service on port $PORT..." -ForegroundColor Green
py -3.12 -m uvicorn main:app --host 0.0.0.0 --port $PORT --reload
