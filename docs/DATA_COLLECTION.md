# Data collection — AppFabric X Runtime Telemetry

## Purpose

Collect **structured Android runtime telemetry** from up to **3 team devices** for analysis (target **50,000–100,000+** rows). Data is stored on each phone locally, then synced to a **single laptop database** via HTTP API (ngrok or cloud).

---

## What is collected (every 2–5 seconds)

| Field | Type | Description |
|-------|------|-------------|
| `record_id` | int | Auto-increment row ID (per device table on phone) |
| `timestamp` | long | Unix ms collection time |
| `cpu_usage` | float | CPU usage % |
| `memory_used` | long | Used RAM (bytes) |
| `memory_available` | long | Available RAM (bytes) |
| `battery_percentage` | int | 0–100 |
| `charging_status` | string | e.g. Charging, Discharging |
| `device_temperature` | float | Device temperature (°C) |
| `process_count` | int | Running processes |
| `foreground_app` | string | Foreground package name (needs Usage Access) |
| `running_apps_count` | int | Running apps count |
| `runtime_state` | string | Classified state (see below) |

### Runtime states

- `NORMAL`
- `HIGH_LOAD`
- `THERMAL_RISK`
- `BATTERY_SAVER`
- `MULTITASKING_STRESS`
- `GAMING_MODE`

---

## Where data is stored

### On the phone (local)

- **Database:** `appfabric_runtime_telemetry.db`
- **Tables (isolated per user):**
  - `device1_runtime_metrics` → User 1
  - `device2_runtime_metrics` → User 2
  - `device3_runtime_metrics` → User 3
- **Collection:** `SystemMonitorService` (foreground, every 2–5 s)
- **CSV export (optional):** `files/telemetry_export/` every 15 rows

### On the laptop (team aggregate)

- **Database:** `server/telemetry-server.db`
- **Single table:** `telemetry_records` (all users, all API uploads)
- **Logical name:** `runtime_metrics`
- **Columns:** same metrics + `device_id`, `device_slot`, `user_label`, `data_source`, `received_at`

---

## Team device assignment

| Phone | App setting | Local table | `device_slot` on server |
|-------|-------------|-------------|-------------------------|
| Team member A | User 1 | `device1_runtime_metrics` | 1 |
| Team member B | User 2 | `device2_runtime_metrics` | 2 |
| Team member C | User 3 | `device3_runtime_metrics` | 3 |

Each phone writes **only its assigned table** locally (isolation). All rows upload to the **same** laptop table `telemetry_records`.

---

## Data flow

```
Phone (foreground service)
    → Room SQLite (per-user table)
    → HTTP POST /api/v1/telemetry
    → ngrok / cloud URL
    → Laptop API (Node.js)
    → telemetry_records (SQLite)
```

---

## API endpoints (laptop)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Server alive |
| POST | `/api/v1/telemetry` | Upload batch from phone |
| GET | `/api/v1/telemetry/all` | Read unified table |
| GET | `/api/v1/stats` | Counts and latest rows |

---

## Permissions required (Android)

- **Notifications** — foreground monitoring
- **Usage access** — foreground app name
- **Internet** — API sync
- **Battery unrestricted** — recommended so collection is not killed

---

## Privacy & sensitive data

- Do **not** commit `config/api-endpoint.txt`, `.env`, or `*.db` files.
- `foreground_app` contains package names (may reveal which apps were used).
- `device_id` is a random app-generated ID (not hardware serial by default).
- For coursework/research: document consent and retention policy for your team.

---

## Export / analysis on laptop

```powershell
# Stats
curl.exe http://localhost:3847/api/v1/stats

# Last 100 rows (all users)
curl.exe "http://localhost:3847/api/v1/telemetry/all?limit=100"
```

Open `server/telemetry-server.db` in [DB Browser for SQLite](https://sqlitebrowser.org/) → table **`telemetry_records`**.
