# AppFabric X — Runtime Telemetry

Flutter + Android app for **continuous runtime telemetry** from up to **3 team phones**, with a **laptop API** that stores all data in **one SQLite table**.

**Package:** `com.example.appfabricx`  
**App name on device:** AppFabric X  
**Version:** 1.0.0

---

## Features

- Foreground collection every **2–5 seconds**
- **12 structured fields** per row (CPU, memory, battery, temperature, foreground app, runtime state, …)
- **Per-user isolated tables** on each phone (`device1/2/3_runtime_metrics`)
- **Unified laptop table** `telemetry_records` via REST API
- **ngrok** support for remote teammates (no USB)
- Live dashboard on phone + API stats on laptop

---

## Project structure

```
appfabricx/
├── android/          # Room DB, foreground service, API upload
├── lib/              # Flutter dashboard
├── server/           # Node.js telemetry API + SQLite
├── config/           # API URL (local, gitignored)
├── scripts/          # Build, ngrok, package APK
├── docs/             # Detailed documentation
└── dist/             # Packaged APK (gitignored)
```

See [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md) for the full tree.

---

## Data collection (important)

All metrics, schema, storage locations, and team assignment are documented here:

**[docs/DATA_COLLECTION.md](docs/DATA_COLLECTION.md)**

Target dataset size: **50,000–100,000+** records across varied workloads.

---

## Quick start (team lead laptop)

### 1. Install dependencies

- [Flutter](https://docs.flutter.dev/get-started/install)
- [Node.js](https://nodejs.org/) 18+
- [ngrok](https://ngrok.com/download) (for remote phones)

### 2. Configure API URL (once per ngrok session)

```powershell
cd C:\Users\Mukil\appfabricx
copy config\api-endpoint.txt.example config\api-endpoint.txt
# Edit config\api-endpoint.txt — paste your https://....ngrok-free.app URL
.\scripts\apply-api-endpoint.ps1
```

### 3. Start laptop API + tunnel

```powershell
cd server
npm install
npm start
```

New terminal:

```powershell
ngrok http 3847
# Or after setting a fixed domain in ngrok.yml:
# ngrok start appfabric
```

Sync URL into project (optional):

```powershell
.\scripts\sync-ngrok-url.ps1
```

### 4. Build & package APK for teammates

```powershell
.\scripts\package-apk-for-download.ps1
```

Output (example):

```
dist/AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk
```

Upload that file to **Google Drive / OneDrive** and share the download link.

### 5. View collected data on laptop

```powershell
curl.exe http://localhost:3847/api/v1/stats
curl.exe "http://localhost:3847/api/v1/telemetry/all?limit=20"
```

Or open `server/telemetry-server.db` → table **`telemetry_records`**.

---

## Quick start (teammate phone)

1. Install **`AppFabricX-Runtime-Telemetry-v1.0.0-b1.apk`** from shared link.
2. Allow **Install unknown apps** + **Usage access** + **Battery unrestricted**.
3. Open **AppFabric X** → choose **User 1, 2, or 3** (one per person).
4. Confirm **Cloud API connected** in the app.
5. Keep the app / monitoring running.

---

## Scripts reference

| Script | Purpose |
|--------|---------|
| `scripts/apply-api-endpoint.ps1` | Apply `config/api-endpoint.txt` to app + server |
| `scripts/sync-ngrok-url.ps1` | Read live ngrok URL and update config |
| `scripts/start-ngrok.ps1` | Start API + ngrok |
| `scripts/package-apk-for-download.ps1` | Build APK with proper filename |
| `scripts/watch-api-laptop.ps1` | Live stats in terminal |

---

## Documentation

| Doc | Topic |
|-----|--------|
| [docs/DATA_COLLECTION.md](docs/DATA_COLLECTION.md) | Schema, storage, team slots |
| [docs/TELEMETRY_API.md](docs/TELEMETRY_API.md) | API endpoints |
| [docs/NGROK.md](docs/NGROK.md) | Remote access with ngrok |
| [docs/STABLE_API_URL.md](docs/STABLE_API_URL.md) | Constant URL workflow |

---

## Security & git

**Never commit:**

- `config/api-endpoint.txt` (real ngrok/cloud URL)
- `server/ngrok-url.json`, `server/telemetry-server.db`
- `.env`, `*.db`, `dist/*.apk`, `build/`

Use the provided `*.example` files as templates. See [.gitignore](.gitignore).

---

## Tech stack

| Layer | Technology |
|-------|------------|
| UI | Flutter (Dart) |
| Native | Kotlin, Room, OkHttp |
| Laptop API | Node.js, Express, better-sqlite3 |
| Tunnel | ngrok (optional) |

---

## License

Academic / project use — update as required by your institution.
