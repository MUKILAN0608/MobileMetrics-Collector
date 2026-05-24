# Poll laptop API for live telemetry (no USB)
# Usage: .\scripts\watch-api-laptop.ps1

param(
    [string]$BaseUrl = "http://localhost:3847",
    [int]$IntervalSeconds = 3
)

while ($true) {
    Clear-Host
    Write-Host "AppFabric API — $BaseUrl" -ForegroundColor Cyan
    Write-Host (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    Write-Host ""
    try {
        $stats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/stats" -Method Get
        Write-Host "Total rows: $($stats.total)" -ForegroundColor Yellow
        Write-Host "By slot:" ($stats.by_slot | ConvertTo-Json -Compress)
        Write-Host ""
        Write-Host "Latest 5:" -ForegroundColor Yellow
        foreach ($r in $stats.latest) {
            Write-Host "  D$($r.device_slot) #$($r.record_id) CPU=$($r.cpu_usage) $($r.runtime_state)"
        }
    } catch {
        Write-Host "Server not reachable. Run: cd server && npm start" -ForegroundColor Red
        Write-Host $_.Exception.Message
    }
    Start-Sleep -Seconds $IntervalSeconds
}
