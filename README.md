# MobileMetrics-Collector

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Flutter-02569B?style=for-the-badge&logo=flutter&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Node.js-339933?style=for-the-badge&logo=nodedotjs&logoColor=white"/>
  <img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white"/>
  <img src="https://img.shields.io/badge/Version-1.0.0-blue?style=for-the-badge"/>
</p>

<p align="center">
  <b>Continuous runtime telemetry collection from Android devices — aggregated over a unified REST API into a single SQLite store.</b><br/>
  Designed for academic research, performance benchmarking, and multi-device behavioral datasets.
</p>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Data Schema](#data-schema)
- [Prerequisites](#prerequisites)
- [Setup — Team Lead (Laptop)](#setup--team-lead-laptop)
- [Setup — Teammates (Phone)](#setup--teammates-phone)
- [API Reference](#api-reference)
- [Scripts Reference](#scripts-reference)
- [Configuration](#configuration)
- [Security & Git Policy](#security--git-policy)
- [Tech Stack](#tech-stack)
- [Documentation Index](#documentation-index)
- [License](#license)

---

## Overview

**MobileMetrics-Collector** (app name: **AppFabric X**, package: `com.example.appfabricx`) is a research-grade telemetry system that collects structured runtime metrics from up to **3 Android phones simultaneously**, every 2–5 seconds, and pushes all data to a centralized **Node.js + SQLite** server running on a team lead's laptop.

The system is built for teams that need a large, labeled, multi-device behavioral dataset — targeting **50,000–100,000+ records** across varied real-world workloads — without requiring USB cables, ADB, or cloud subscriptions. An **ngrok** tunnel makes remote collection possible from anywhere.

Each phone is assigned a unique user slot (`device1`, `device2`, `device3`), keeping data isolated per device while being unified in one table on the server.

---

## Features

| Feature | Detail |
|---|---|
| Continuous collection | Every 2–5 seconds via a foreground Android service |
| 12 structured fields | CPU, memory, battery, temperature, foreground app, runtime state, and more |
| Per-device isolation | Separate Room DB tables per user slot on each phone |
| Unified server table | All data lands in one `telemetry_records` SQLite table |
| REST API | Express endpoints for ingestion and query |
| ngrok support | Expose local server to remote teammates over HTTPS |
| Live dashboard | Flutter UI showing real-time metrics on the phone |
| Packaged APK | One-command build script produces a shareable `.apk` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Team Phones (Android)                        │
│                                                                     │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐            │
│  │   Device 1   │   │   Device 2   │   │   Device 3   │            │
│  │ Flutter UI   │   │ Flutter UI   │   │ Flutter UI   │            │
│  │ Foreground   │   │ Foreground   │   │ Foreground   │            │
│  │ Service      │   │ Service      │   │ Service      │            │
│  │ Room DB      │   │ Room DB      │   │ Room DB      │            │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘            │
│         │ OkHttp (HTTPS)   │                  │                     │
└─────────┼──────────────────┼──────────────────┼─────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Team Lead Laptop                                │
│                                                                     │
│   ┌───────────────────────────────────────────────────────────┐     │
│   │  ngrok tunnel  →  localhost:3847                          │     │
│   │                                                           │     │
│   │  Node.js + Express (telemetry-server)                     │     │
│   │  POST /api/v1/telemetry  ──►  SQLite (telemetry_records)  │     │
│   │  GET  /api/v1/stats                                       │     │
│   │  GET  /api/v1/telemetry/all                               │     │
│   └───────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
MobileMetrics-Collector/
│
├── android/                        # Native Android layer
│   ├── app/src/main/
│   │   ├── kotlin/com/example/appfabricx/
│   │   │   ├── TelemetryService.kt     # Foreground service — collects metrics
│   │   │   ├── ApiUploader.kt          # OkHttp upload to REST API
│   │   │   ├── db/
│   │   │   │   ├── TelemetryDatabase.kt
│   │   │   │   ├── TelemetryDao.kt
│   │   │   │   └── TelemetryRecord.kt  # Room entity — 12-field schema
│   │   │   └── MainActivity.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle
│
├── lib/                            # Flutter layer (Dart)
│   ├── main.dart
│   ├── dashboard/
│   │   ├── dashboard_screen.dart   # Live metrics display
│   │   └── metric_card.dart
│   └── config/
│       └── api_config.dart         # Reads injected API URL
│
├── server/                         # Node.js telemetry API
│   ├── index.js                    # Express entry point
│   ├── routes/
│   │   └── telemetry.js            # POST + GET route handlers
│   ├── db/
│   │   └── database.js             # better-sqlite3 setup, schema init
│   ├── package.json
│   └── telemetry-server.db         # SQLite file (gitignored)
│
├── config/
│   ├── api-endpoint.txt.example    # Template — copy and fill in URL
│   └── api-endpoint.txt            # Real URL (gitignored)
│
├── scripts/
│   ├── apply-api-endpoint.ps1      # Injects API URL into app + server
│   ├── sync-ngrok-url.ps1          # Reads live ngrok URL, updates config
│   ├── start-ngrok.ps1             # Starts API server + ngrok tunnel
│   ├── package-apk-for-download.ps1# Builds and names the release APK
│   └── watch-api-laptop.ps1        # Polls and displays live API stats
│
├── docs/
│   ├── DATA_COLLECTION.md          # Schema, fields, storage, team slots
│   ├── TELEMETRY_API.md            # Full API endpoint reference
│   ├── NGROK.md                    # ngrok setup and troubleshooting
│   ├── STABLE_API_URL.md           # How to use a fixed ngrok domain
│   └── PROJECT_STRUCTURE.md        # Full annotated directory tree
│
├── dist/                           # Packaged APK output (gitignored)
│   └── AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk
│
├── .gitignore
└── README.md
```

---

## Data Schema

Every record collected by a device is stored in the following structure:

### Phone-side (Room DB)

Each device maintains its own table (`device1_runtime_metrics`, `device2_runtime_metrics`, `device3_runtime_metrics`) with the same schema:

| Field | Type | Description |
|---|---|---|
| `id` | INTEGER PK | Auto-incremented local row ID |
| `timestamp` | TEXT | ISO-8601 collection timestamp |
| `device_id` | TEXT | `device1`, `device2`, or `device3` |
| `cpu_usage` | REAL | CPU utilization (0.0–100.0%) |
| `memory_used_mb` | REAL | RAM in use (MB) |
| `memory_total_mb` | REAL | Total RAM available (MB) |
| `battery_level` | INTEGER | Battery percentage (0–100) |
| `battery_charging` | INTEGER | 1 = charging, 0 = not charging |
| `battery_temp_celsius` | REAL | Battery temperature (°C) |
| `foreground_app` | TEXT | Package name of the active foreground app |
| `runtime_state` | TEXT | App runtime state label |
| `upload_status` | TEXT | `pending`, `uploaded`, or `failed` |

### Laptop-side (SQLite — `telemetry_records`)

The server merges all device uploads into one flat table with the same 12 fields, plus a server-assigned `received_at` timestamp.

Target size: **50,000–100,000+ rows** across all devices and workloads.

---

## Prerequisites

### Team Lead Laptop

| Tool | Version | Install |
|---|---|---|
| Flutter SDK | Latest stable | [flutter.dev](https://docs.flutter.dev/get-started/install) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) |
| ngrok | Latest | [ngrok.com/download](https://ngrok.com/download) |
| PowerShell | 5.1+ | Pre-installed on Windows |

### Teammate Phones

- Android 8.0 (API 26) or higher
- `Install unknown apps` permission enabled
- `Usage access` permission granted to AppFabric X
- Battery optimization **disabled** for AppFabric X (unrestricted background)

---

## Setup — Team Lead (Laptop)

### Step 1 — Install dependencies

```powershell
# Clone the repository
git clone https://github.com/MUKILAN0608/MobileMetrics-Collector.git
cd MobileMetrics-Collector

# Install server dependencies
cd server
npm install
cd ..
```

### Step 2 — Configure the API endpoint

```powershell
# Copy the example config
copy config\api-endpoint.txt.example config\api-endpoint.txt

# Edit the file and paste your ngrok HTTPS URL, e.g.:
# https://abc123.ngrok-free.app
notepad config\api-endpoint.txt

# Apply the URL to the Flutter app and server config
.\scripts\apply-api-endpoint.ps1
```

### Step 3 — Start the server and tunnel

**Terminal 1 — API server:**

```powershell
cd server
npm start
# Server starts on http://localhost:3847
```

**Terminal 2 — ngrok tunnel:**

```powershell
ngrok http 3847
# Copy the https://....ngrok-free.app URL shown

# If you have a fixed ngrok domain configured:
# ngrok start appfabric
```

**Optional — sync URL automatically:**

```powershell
.\scripts\sync-ngrok-url.ps1
```

### Step 4 — Build and distribute the APK

```powershell
.\scripts\package-apk-for-download.ps1
```

Output:

```
dist/AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk
```

Upload this file to **Google Drive** or **OneDrive** and share the download link with your team.

### Step 5 — Monitor collected data

```powershell
# Summary stats
curl.exe http://localhost:3847/api/v1/stats

# Latest 20 records from all devices
curl.exe "http://localhost:3847/api/v1/telemetry/all?limit=20"

# Live polling dashboard in terminal
.\scripts\watch-api-laptop.ps1
```

Or open `server/telemetry-server.db` directly in any SQLite viewer (e.g. [DB Browser for SQLite](https://sqlitebrowser.org/)) and query the `telemetry_records` table.

---

## Setup — Teammates (Phone)

1. Download **`AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk`** from the shared link.
2. On the phone, go to **Settings → Install unknown apps** → allow your browser/file manager.
3. Open the APK file and install it.
4. Launch **AppFabric X**.
5. Grant **Usage access** when prompted (Settings → Apps → Special app access → Usage access → AppFabric X).
6. Disable battery optimization: Settings → Battery → AppFabric X → Unrestricted.
7. Select your assigned slot: **User 1**, **User 2**, or **User 3** (one slot per person — do not share).
8. Confirm the status indicator shows **Cloud API connected**.
9. Keep the app running (or leave it active in the background) during your collection sessions.

---

## API Reference

Base URL: `http://localhost:3847` (or your ngrok HTTPS URL for remote access)

### `POST /api/v1/telemetry`

Submit a telemetry record from a device.

**Request body (JSON):**

```json
{
  "device_id": "device1",
  "timestamp": "2025-05-24T10:30:00.000Z",
  "cpu_usage": 34.7,
  "memory_used_mb": 2048.5,
  "memory_total_mb": 6144.0,
  "battery_level": 81,
  "battery_charging": 0,
  "battery_temp_celsius": 36.2,
  "foreground_app": "com.google.android.youtube",
  "runtime_state": "active",
  "upload_status": "pending"
}
```

**Response:**

```json
{ "success": true, "id": 4821 }
```

---

### `GET /api/v1/stats`

Returns aggregate counts and per-device breakdowns.

**Response:**

```json
{
  "total_records": 48210,
  "by_device": {
    "device1": 16423,
    "device2": 15987,
    "device3": 15800
  },
  "latest_record_at": "2025-05-24T10:31:42.000Z"
}
```

---

### `GET /api/v1/telemetry/all`

Retrieve records with optional filters.

**Query parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `limit` | integer | 100 | Max records to return |
| `device_id` | string | — | Filter by `device1`, `device2`, or `device3` |
| `since` | ISO-8601 string | — | Return records after this timestamp |

**Example:**

```powershell
curl.exe "http://localhost:3847/api/v1/telemetry/all?limit=50&device_id=device2"
```

See [docs/TELEMETRY_API.md](docs/TELEMETRY_API.md) for the full endpoint reference.

---

## Scripts Reference

| Script | Purpose |
|---|---|
| `scripts/apply-api-endpoint.ps1` | Reads `config/api-endpoint.txt` and injects the URL into the Flutter app and server config |
| `scripts/sync-ngrok-url.ps1` | Reads the live ngrok local API, extracts the public HTTPS URL, and writes it to config |
| `scripts/start-ngrok.ps1` | Convenience script: starts the API server and ngrok tunnel together |
| `scripts/package-apk-for-download.ps1` | Runs `flutter build apk --release` and copies the output to `dist/` with the versioned filename |
| `scripts/watch-api-laptop.ps1` | Polls `/api/v1/stats` every 5 seconds and prints a live summary in the terminal |

---

## Configuration

### `config/api-endpoint.txt`

Contains the full HTTPS base URL of the telemetry server. This is the only file you need to edit per ngrok session.

```
https://abc123.ngrok-free.app
```

This file is **gitignored**. Never commit it. Use `config/api-endpoint.txt.example` as a reference.

### Fixed ngrok domain (optional)

To avoid updating the URL each session, configure a [reserved ngrok domain](https://ngrok.com/docs/network-edge/domains/) in your `ngrok.yml`:

```yaml
tunnels:
  appfabric:
    proto: http
    addr: 3847
    hostname: your-fixed-domain.ngrok-free.app
```

See [docs/STABLE_API_URL.md](docs/STABLE_API_URL.md) for the full workflow.

---

## Security & Git Policy

**Never commit the following files:**

| File / Pattern | Reason |
|---|---|
| `config/api-endpoint.txt` | Contains real ngrok / cloud URL |
| `server/ngrok-url.json` | Auto-generated live URL cache |
| `server/telemetry-server.db` | Contains all collected data |
| `.env` | Environment secrets |
| `*.db` | Any SQLite database files |
| `dist/*.apk` | Built APK binaries |
| `build/` | Flutter build artifacts |

All of the above are covered by `.gitignore`. Use the provided `*.example` files as safe, committable templates.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| UI | Flutter (Dart) | Cross-platform live dashboard on device |
| Native collection | Kotlin | Foreground service, system metric APIs |
| Local storage | Room (SQLite) | Per-device metric buffering |
| Upload | OkHttp | HTTPS POST to laptop API |
| Laptop API | Node.js + Express | REST ingestion and query server |
| Server DB | better-sqlite3 | Unified `telemetry_records` table |
| Tunnel | ngrok | HTTPS access without port forwarding |

---

## Documentation Index

| Document | Contents |
|---|---|
| [docs/DATA_COLLECTION.md](docs/DATA_COLLECTION.md) | Full field schema, storage locations, team slot assignment |
| [docs/TELEMETRY_API.md](docs/TELEMETRY_API.md) | All API endpoints, request/response formats, error codes |
| [docs/NGROK.md](docs/NGROK.md) | ngrok installation, configuration, and troubleshooting |
| [docs/STABLE_API_URL.md](docs/STABLE_API_URL.md) | How to use a fixed domain to avoid reconfiguring each session |
| [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md) | Full annotated directory and file tree |

---

## License

Academic / research use. Update the license header to match your institution's requirements before submission or publication.
