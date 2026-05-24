# Scripts

Run from repository root: `.\scripts\<name>.ps1`

| Script | Description |
|--------|-------------|
| `apply-api-endpoint.ps1` | Read `config/api-endpoint.txt` → update Dart/Kotlin/server config |
| `sync-ngrok-url.ps1` | Read ngrok agent (port 4040) → update `api-endpoint.txt` + apply |
| `start-ngrok.ps1` | Start API server and ngrok tunnel |
| `package-apk-for-download.ps1` | Build APK → `dist/AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk` |
| `watch-api-laptop.ps1` | Poll `localhost:3847/api/v1/stats` every 3s |
| `get-ngrok-url.ps1` | Print current ngrok HTTPS URL |
| `merge-telemetry-databases.ps1` | Notes on DB merge (runs on server start) |
