# Reads live ngrok URL and pushes it into the app + config files + connected phone (adb).
# Usage: .\scripts\sync-ngrok-url.ps1

param(
    [switch]$InstallApk
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$ngrokUrl = $null

try {
    $tunnels = Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 5
    $https = $tunnels.tunnels | Where-Object { $_.proto -eq "https" } | Select-Object -First 1
    $ngrokUrl = $https.public_url
} catch {
    Write-Host "ngrok is not running. Start: ngrok http 3847" -ForegroundColor Red
    exit 1
}

if (-not $ngrokUrl) {
    Write-Host "No HTTPS ngrok tunnel found." -ForegroundColor Red
    exit 1
}

Write-Host "Current ngrok URL: $ngrokUrl" -ForegroundColor Green

# Write single source of truth and regenerate app configs
$endpointFile = Join-Path $repoRoot "config\api-endpoint.txt"
Set-Content -Path $endpointFile -Value $ngrokUrl -Encoding UTF8 -NoNewline
& (Join-Path $PSScriptRoot "apply-api-endpoint.ps1")

# Push to phone via adb (USB debugging)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adb) {
    $devices = & $adb devices | Select-String "device$"
    if ($devices) {
        & $adb shell am broadcast -n "com.example.appfabricx/.telemetry.ApiUrlUpdateReceiver" `
            -a "com.example.appfabricx.SET_API_URL" --es base_url $ngrokUrl
        Write-Host "Sent URL to connected phone via adb." -ForegroundColor Green
    } else {
        Write-Host "No USB phone - remote users need latest APK or app will auto-discover fallbacks." -ForegroundColor Yellow
    }
}

if ($InstallApk) {
    Push-Location $repoRoot
    flutter build apk --debug
    flutter install --debug
    Pop-Location
}

Write-Host ""
Write-Host "Done. Rebuild/install APK for teammates without USB:" -ForegroundColor Yellow
Write-Host '  flutter build apk --debug; flutter install --debug'
