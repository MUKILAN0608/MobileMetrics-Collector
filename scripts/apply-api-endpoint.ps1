# Single source of truth for API URL -> updates app + server config.
# Edit config/api-endpoint.txt then run: .\scripts\apply-api-endpoint.ps1

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
$endpointFile = Join-Path $repoRoot "config\api-endpoint.txt"

$exampleFile = Join-Path $repoRoot "config\api-endpoint.txt.example"
if (-not (Test-Path $endpointFile)) {
    if (Test-Path $exampleFile) {
        Copy-Item $exampleFile $endpointFile
        Write-Host "Created $endpointFile from example — edit your real API URL first." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Missing $endpointFile (copy from api-endpoint.txt.example)" -ForegroundColor Red
    exit 1
}

if ((Get-Content $endpointFile -Raw) -match 'YOUR-API-URL-HERE') {
    Write-Host "Edit config/api-endpoint.txt with your real ngrok/cloud URL first." -ForegroundColor Red
    exit 1
}

$url = (Get-Content $endpointFile -Raw).Trim()
$url = $url -replace '/$', ''
if ($url -notmatch '^https?://') {
    $url = "https://$url"
}

Write-Host "Applying constant API URL:" -ForegroundColor Cyan
Write-Host "  $url"

# Dart
$dartPath = Join-Path $repoRoot "lib\config\telemetry_api_defaults.dart"
@(
    '/// CONSTANT team API URL — edit config/api-endpoint.txt then run scripts/apply-api-endpoint.ps1',
    'class TelemetryApiDefaults {',
    '  TelemetryApiDefaults._();',
    '',
    "  static const String defaultBaseUrl = '$url';",
    '}',
    ''
) | Set-Content -Path $dartPath -Encoding UTF8

# Kotlin
$ktPath = Join-Path $repoRoot "android\app\src\main\java\com\example\appfabricx\telemetry\TelemetryApiDefaults.kt"
@(
    'package com.example.appfabricx.telemetry',
    '',
    'object TelemetryApiDefaults {',
    '    /** Must match config/api-endpoint.txt — run scripts/apply-api-endpoint.ps1 */',
    "    const val DEFAULT_BASE_URL = `"$url`"",
    '}',
    ''
) | Set-Content -Path $ktPath -Encoding UTF8

# Server bootstrap file
$jsonPath = Join-Path $repoRoot "server\ngrok-url.json"
@{ public_url = $url; updated_at = (Get-Date).ToUniversalTime().ToString("o") } |
    ConvertTo-Json | Set-Content -Path $jsonPath -Encoding UTF8

Write-Host "Updated Dart, Kotlin, and server/ngrok-url.json" -ForegroundColor Green
Write-Host "Rebuild APK: flutter build apk --debug" -ForegroundColor Yellow
