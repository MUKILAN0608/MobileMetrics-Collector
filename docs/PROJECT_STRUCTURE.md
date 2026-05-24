# Project structure

```
appfabricx/
│
├── android/                    # Native Android layer
│   └── app/src/main/
│       ├── java/.../data/      # Room DB entities, DAO, schema
│       ├── java/.../telemetry/ # Collector, API sync, foreground service
│       └── kotlin/.../         # MainActivity, Flutter channel
│
├── lib/                        # Flutter application
│   ├── config/                 # API URL defaults (generated from config/)
│   ├── models/                 # Runtime metric models
│   ├── screens/                # Dashboard UI
│   ├── services/               # MethodChannel telemetry service
│   └── widgets/                # Device metrics tables
│
├── server/                     # Laptop telemetry API (Node.js)
│   ├── index.js                # REST API + SQLite writes
│   ├── db-unified.js           # Single-table migrations/imports
│   ├── package.json
│   ├── .env.example            # Optional API_KEY (copy to .env)
│   └── ngrok-url.json.example  # Local tunnel URL (copy to ngrok-url.json)
│
├── config/                     # Local team configuration (not in git)
│   ├── api-endpoint.txt.example
│   └── api-endpoint.txt        # Your real URL (gitignored)
│
├── scripts/                    # Automation (Windows PowerShell)
│   ├── apply-api-endpoint.ps1
│   ├── sync-ngrok-url.ps1
│   ├── start-ngrok.ps1
│   ├── package-apk-for-download.ps1
│   └── watch-api-laptop.ps1
│
├── docs/                       # Documentation
│   ├── DATA_COLLECTION.md      # Dataset & schema (main reference)
│   ├── TELEMETRY_API.md
│   ├── NGROK.md
│   └── STABLE_API_URL.md
│
├── dist/                       # Packaged APK output (gitignored)
├── test/                       # Flutter tests
├── ngrok.yml                   # Optional fixed ngrok domain config
├── pubspec.yaml
└── README.md
```

## What is committed vs local-only

| Committed (safe) | Local only (gitignored) |
|------------------|-------------------------|
| Source code | `config/api-endpoint.txt` |
| `*.example` config templates | `server/ngrok-url.json` |
| Documentation | `server/telemetry-server.db` |
| Scripts | `server/node_modules/` |
| | `dist/*.apk`, `build/` |
| | `.env`, `*.db` |
