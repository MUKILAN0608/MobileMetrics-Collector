# Live telemetry viewer on laptop (phone must be connected via USB or wireless adb)
# Usage: .\scripts\watch-telemetry-laptop.ps1
# Optional: .\scripts\watch-telemetry-laptop.ps1 -IntervalSeconds 5

param(
    [int]$IntervalSeconds = 3,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.example.appfabricx",
    [string]$DbName = "appfabric_runtime_telemetry.db",
    [string]$ExportDir = "telemetry_export"
)

$ErrorActionPreference = "SilentlyContinue"
$repoRoot = Split-Path $PSScriptRoot -Parent
$localDb = Join-Path $repoRoot "telemetry-live.db"

function Write-Header {
    Clear-Host
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " AppFabric X - Live DB on Laptop" -ForegroundColor Cyan
    Write-Host " Refresh every $IntervalSeconds s | Ctrl+C to stop" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Invoke-Adb {
    param([string[]]$Args)
    & $AdbPath @Args 2>&1
}

function Test-DeviceConnected {
    $devices = Invoke-Adb @("devices")
    return ($devices -match "device$") -and ($devices -notmatch "unauthorized")
}

function Pull-Database {
    $tmp = "$localDb.tmp"
    $args = @("exec-out", "run-as", $Package, "cat", "databases/$DbName")
    $p = Start-Process -FilePath $AdbPath -ArgumentList $args -RedirectStandardOutput $tmp -NoNewWindow -Wait -PassThru
    if ($p.ExitCode -ne 0 -or -not (Test-Path $tmp)) { return $false }
    $size = (Get-Item $tmp).Length
    if ($size -lt 100) { Remove-Item $tmp -Force -ErrorAction SilentlyContinue; return $false }
    Move-Item -Force $tmp $localDb
    return $true
}

function Query-Sqlite {
    param([string]$Sql)
    $sqlite3 = Get-Command sqlite3 -ErrorAction SilentlyContinue
    if (-not $sqlite3) { return $null }
    return & sqlite3 $localDb $Sql 2>$null
}

function Show-LiveStats {
    Write-Host "Local copy: $localDb" -ForegroundColor Gray
    Write-Host "Pulled at:    $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
    Write-Host ""

    $counts = Query-Sqlite @"
SELECT 'device1', COUNT(*) FROM device1_runtime_metrics
UNION ALL SELECT 'device2', COUNT(*) FROM device2_runtime_metrics
UNION ALL SELECT 'device3', COUNT(*) FROM device3_runtime_metrics;
"@
    if ($counts) {
        Write-Host "--- Row counts (live) ---" -ForegroundColor Yellow
        $counts | ForEach-Object { Write-Host "  $_" }
        Write-Host ""
    }

    $latest = Query-Sqlite @"
SELECT 'device' || slot AS tbl, record_id, timestamp, cpu_usage, battery_percentage,
       runtime_state, substr(foreground_app,1,40)
FROM (
  SELECT 1 AS slot, * FROM device1_runtime_metrics ORDER BY record_id DESC LIMIT 1
  UNION ALL
  SELECT 2, * FROM device2_runtime_metrics ORDER BY record_id DESC LIMIT 1
  UNION ALL
  SELECT 3, * FROM device3_runtime_metrics ORDER BY record_id DESC LIMIT 1
);
"@
    if ($latest) {
        Write-Host "--- Latest row per table ---" -ForegroundColor Yellow
        $latest | ForEach-Object { Write-Host "  $_" }
        Write-Host ""
    }

    $recent = Query-Sqlite @"
SELECT record_id, timestamp, cpu_usage, memory_used, memory_available,
       battery_percentage, charging_status, device_temperature,
       process_count, substr(foreground_app,1,30), running_apps_count, runtime_state
FROM device1_runtime_metrics ORDER BY record_id DESC LIMIT 3;
"@
    if ($recent) {
        Write-Host "--- Last 3 rows: device1_runtime_metrics ---" -ForegroundColor Yellow
        Write-Host "  record_id|timestamp|cpu|mem_used|mem_avail|bat|charge|temp|proc|fg_app|apps|state"
        $recent | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host "Install sqlite3 for full queries, or open telemetry-live.db in DB Browser for SQLite." -ForegroundColor DarkYellow
        Write-Host "Download: https://sqlitebrowser.org/" -ForegroundColor DarkYellow
    }
}

# --- main ---
if (-not (Test-Path $AdbPath)) {
    Write-Host "adb not found at: $AdbPath" -ForegroundColor Red
    Write-Host "Set path: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    exit 1
}

Write-Host "Waiting for phone (USB debugging)..." -ForegroundColor Gray
while (-not (Test-DeviceConnected)) {
    Start-Sleep -Seconds 2
    Write-Host "." -NoNewline
}
Write-Host "`nDevice connected.`n" -ForegroundColor Green

while ($true) {
    Write-Header
    if (Pull-Database) {
        Show-LiveStats
    } else {
        Write-Host "Could not pull DB. Is the app installed and has it collected data?" -ForegroundColor Red
        Write-Host "Try: open appfabricx on phone and wait 10 seconds." -ForegroundColor Red
    }
    Start-Sleep -Seconds $IntervalSeconds
}
