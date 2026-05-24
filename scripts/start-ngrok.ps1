# Start telemetry API + ngrok tunnel for remote phones
# Usage: .\scripts\start-ngrok.ps1
#
# Prereqs:
#   1) npm install in server/ (once)
#   2) ngrok installed: winget install ngrok.ngrok  OR https://ngrok.com/download
#   3) ngrok authtoken (once): ngrok config add-authtoken YOUR_TOKEN

param(
    [int]$Port = 3847
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$serverDir = Join-Path $repoRoot "server"

function Test-PortListening {
    param([int]$P)
    return [bool](netstat -ano | Select-String ":$P\s" | Select-String "LISTENING")
}

function Start-ApiServer {
    if (Test-PortListening -P $Port) {
        Write-Host "API already listening on port $Port" -ForegroundColor Green
        return
    }
    Write-Host "Starting API server on port $Port..." -ForegroundColor Cyan
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "npm"
    $psi.Arguments = "start"
    $psi.WorkingDirectory = $serverDir
    $psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Minimized
    $psi.UseShellExecute = $true
    [void][System.Diagnostics.Process]::Start($psi)
    Start-Sleep -Seconds 3
    if (-not (Test-PortListening -P $Port)) {
        throw "API did not start. Run manually: cd server; npm start"
    }
    Write-Host "API started." -ForegroundColor Green
}

function Get-NgrokCmd {
    $cmd = Get-Command ngrok -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $local = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ngrok.exe"
    if (Test-Path $local) { return $local }
    return $null
}

# --- main ---
if (-not (Test-Path (Join-Path $serverDir "node_modules"))) {
    Write-Host "Installing server dependencies..." -ForegroundColor Cyan
    Push-Location $serverDir
    npm install
    Pop-Location
}

Start-ApiServer

$ngrok = Get-NgrokCmd
if (-not $ngrok) {
    Write-Host ""
    Write-Host "ngrok not found. Install it:" -ForegroundColor Red
    Write-Host "  winget install ngrok.ngrok"
    Write-Host "  OR download: https://ngrok.com/download"
    Write-Host ""
    Write-Host "Then sign in (free):" -ForegroundColor Yellow
    Write-Host "  ngrok config add-authtoken <token from https://dashboard.ngrok.com/get-started/your-authtoken>"
    Write-Host ""
    Write-Host "API is running locally. Manual tunnel:" -ForegroundColor Yellow
    Write-Host "  ngrok http $Port"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Starting ngrok -> localhost:$Port" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Copy the https://....ngrok-free.app URL into every phone:" -ForegroundColor Yellow
Write-Host "  App -> Cloud API sync -> API base URL -> Save -> Test"
Write-Host ""
Write-Host "User 1 phone: device slot 1 | User 2: slot 2 | User 3: slot 3"
Write-Host "Keep this window open. Laptop must stay on."
Write-Host ""
Write-Host "Local stats:  curl http://localhost:$Port/api/v1/stats"
Write-Host "ngrok UI:     http://127.0.0.1:4040"
Write-Host ""

& $ngrok http $Port
