# Build and copy APK with a proper filename for team download/share.
# Usage: .\scripts\package-apk-for-download.ps1

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$version = "1.0.0"
$build = "1"
$apkName = "AppFabricX-Runtime-Telemetry-v${version}-b${build}.apk"

Push-Location $repoRoot

$endpointFile = Join-Path $repoRoot "config\api-endpoint.txt"
if (Test-Path $endpointFile) {
    & (Join-Path $PSScriptRoot "apply-api-endpoint.ps1")
}

Write-Host "Building APK..." -ForegroundColor Cyan
flutter build apk --debug
Pop-Location

$src = Join-Path $repoRoot "build\app\outputs\flutter-apk\app-debug.apk"
if (-not (Test-Path $src)) {
    Write-Host "Build failed - APK not found" -ForegroundColor Red
    exit 1
}

$desktop = [Environment]::GetFolderPath("Desktop")
$downloads = [Environment]::GetFolderPath("Personal")
if (-not $downloads) { $downloads = Join-Path $env:USERPROFILE "Downloads" }

$destDesktop = Join-Path $desktop $apkName
$destDownloads = Join-Path $downloads $apkName
$destBuild = Join-Path $repoRoot "dist\$apkName"

New-Item -ItemType Directory -Force -Path (Split-Path $destBuild) | Out-Null

Copy-Item $src $destDesktop -Force
Copy-Item $src $destDownloads -Force
Copy-Item $src $destBuild -Force

Write-Host ""
Write-Host "AppFabric X - ready to share" -ForegroundColor Green
Write-Host "================================"
Write-Host "File name: $apkName"
Write-Host ""
Write-Host "Copy from any of these:" -ForegroundColor Cyan
Write-Host "  Desktop:   $destDesktop"
Write-Host "  Downloads: $destDownloads"
Write-Host "  Project:   $destBuild"
Write-Host ""
Write-Host "Upload to Google Drive / OneDrive and send the link to teammates."
