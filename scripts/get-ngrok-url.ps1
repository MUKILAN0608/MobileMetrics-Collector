# Print the current ngrok HTTPS URL (for phone app / config files)
$tunnels = Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -ErrorAction Stop
$https = $tunnels.tunnels | Where-Object { $_.proto -eq "https" } | Select-Object -First 1
if (-not $https) {
    Write-Host "No ngrok tunnel. Run: ngrok http 3847" -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "Paste this in the app (Cloud API sync -> Connect API):" -ForegroundColor Green
Write-Host $https.public_url
Write-Host ""
Write-Host "Local API:  curl http://localhost:3847/health"
Write-Host "Test ngrok: curl.exe -H `"ngrok-skip-browser-warning: true`" $($https.public_url)/health"
