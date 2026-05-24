# Telemetry API sync (no USB)

Phone uploads each new SQLite row to your laptop (or any server) using **HTTP POST**. Works on **mobile data** or Wi‑Fi — no USB, no ADB pull.

## 1. Start API server on laptop

```powershell
cd C:\Users\Mukil\appfabricx\server
npm install
npm start
```

Server listens on `http://0.0.0.0:3847` and stores data in `server/telemetry-server.db`.

Optional API key:

```powershell
$env:API_KEY = "my-secret-key"
npm start
```

## 2. Find laptop IP

```powershell
ipconfig
```

Use your Wi‑Fi IPv4, e.g. `192.168.1.10`.

## 3. Configure phone app

1. Install/run the app.
2. Open **Cloud API sync (no USB)**.
3. Set **API base URL**: `http://192.168.1.10:3847` (no trailing slash).
4. Tap **Save API** → **Test connection**.
5. Start monitoring — each new row POSTs automatically.

## 4. Watch live data on laptop

```powershell
.\scripts\watch-api-laptop.ps1
```

Or browser/curl:

```powershell
curl http://localhost:3847/api/v1/stats
curl "http://localhost:3847/api/v1/telemetry?device_slot=1&limit=10"
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Server alive |
| POST | `/api/v1/telemetry` | Batch upload from phone |
| GET | `/api/v1/telemetry?device_slot=1&limit=50` | Read stored rows |
| GET | `/api/v1/stats` | Counts + latest rows |

POST body (JSON):

```json
{
  "device_id": "appfabric-uuid",
  "device_slot": 1,
  "user_label": "User 1",
  "records": [
    {
      "record_id": 42,
      "table_name": "device1_runtime_metrics",
      "timestamp": 1710000000000,
      "cpu_usage": 35.2,
      "memory_used": 2147483648,
      "memory_available": 1073741824,
      "battery_percentage": 78,
      "charging_status": "Charging",
      "device_temperature": 32.5,
      "process_count": 120,
      "foreground_app": "com.example.app",
      "running_apps_count": 45,
      "runtime_state": "NORMAL"
    }
  ]
}
```

Header (if `API_KEY` set): `X-Api-Key: my-secret-key`

## Remote team (ngrok) — recommended

See **[NGROK.md](NGROK.md)** for full steps.

```powershell
cd C:\Users\Mukil\appfabricx
.\scripts\start-ngrok.ps1
```

Share the `https://….ngrok-free.app` URL with all phones (same URL; different User 1/2/3 per device).

## Mobile data only (without ngrok)

Laptop local IP (`192.168.x.x`) is **not** reachable from mobile data. Use ngrok or deploy `server/` to the cloud.

## Firewall

Allow inbound TCP **3847** on Windows Firewall for private networks.
