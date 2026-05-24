# Merge old DB files into server/telemetry-server.db (one table: telemetry_records)
# Usage: .\scripts\merge-telemetry-databases.ps1

$repoRoot = Split-Path $PSScriptRoot -Parent
$serverDir = Join-Path $repoRoot "server"

Write-Host "Stop npm start if running, then run:" -ForegroundColor Yellow
Write-Host "  cd server; npm start" -ForegroundColor Yellow
Write-Host ""
Write-Host "On server start, these files are auto-imported into telemetry_records:" -ForegroundColor Cyan
@(
    "server\telemetry-server-old.db",
    "telemetry.db",
    "telemetry-live.db"
) | ForEach-Object { Write-Host "  - $_" }

Write-Host ""
Write-Host "View unified data:" -ForegroundColor Green
Write-Host "  curl.exe http://localhost:3847/api/v1/stats"
Write-Host "  curl.exe `"http://localhost:3847/api/v1/telemetry/all?limit=20`""
