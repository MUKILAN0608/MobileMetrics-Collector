# Remote sync with ngrok

Use ngrok so **all 3 phones** (anywhere, mobile data) POST telemetry to **your laptop**, which stores everything in `server/telemetry-server.db`.

## One-time setup

### 1. ngrok account (free)

1. Sign up: https://dashboard.ngrok.com/signup  
2. Copy your authtoken: https://dashboard.ngrok.com/get-started/your-authtoken  
3. In PowerShell:

```powershell
ngrok config add-authtoken YOUR_TOKEN_HERE
```

### 2. Install ngrok (if needed)

```powershell
winget install ngrok.ngrok
```

Or download: https://ngrok.com/download

### 3. API server dependencies (once)

```powershell
cd C:\Users\Mukil\appfabricx\server
npm install
```

---

## Every session (you run this on your laptop)

```powershell
cd C:\Users\Mukil\appfabricx
.\scripts\start-ngrok.ps1
```

This will:

- Start the API on port **3847** (if not already running)
- Start **ngrok** and show a public URL like `https://abc123.ngrok-free.app`

**Keep that PowerShell window open.** Closing it stops the tunnel.

Open http://127.0.0.1:4040 to see requests and copy the HTTPS URL.

---

## Configure each phone (all 3 users)

1. Install the AppFabric X app (monitoring starts automatically).  
2. App **auto-connects** to ngrok on launch (URL in `lib/config/telemetry_api_defaults.dart`).  
3. Optional: **Cloud API sync** → paste new ngrok URL → **Connect API**.  
4. **Device assignment**: User 1 / 2 / 3 per phone.  
5. Leave monitoring running — uploads every 2–5s.

Share the **same ngrok URL** with all teammates. Each person uses a different device slot.

---

## Watch data on your laptop

```powershell
curl http://localhost:3847/api/v1/stats
```

Or:

```powershell
.\scripts\watch-api-laptop.ps1
```

Database file: `C:\Users\Mukil\appfabricx\server\telemetry-server.db`

---

## Important notes

| Topic | Detail |
|--------|--------|
| **URL changes** | Free ngrok gives a **new URL each time** you restart ngrok. Everyone must update the app URL after each restart. |
| **Paid ngrok** | Fixed subdomain (e.g. `https://appfabric.ngrok.app`) so URL never changes. |
| **Laptop** | Must stay on, on the internet, with ngrok + API running. |
| **HTTPS** | Use the **https://** URL from ngrok, not http. |
| **Optional API key** | If you set `$env:API_KEY="secret"` before starting the server, set the same key in each phone app. |

---

## Troubleshooting

**`EADDRINUSE` on 3847** — server already running (good):

```powershell
curl http://localhost:3847/health
```

**Test fails on phone** — wrong URL, ngrok stopped, or laptop offline.

**ngrok browser warning** — the app sends `ngrok-skip-browser-warning` automatically; rebuild the APK if you use an old build.

**Manual start** (two terminals):

```powershell
# Terminal 1
cd C:\Users\Mukil\appfabricx\server
npm start

# Terminal 2
ngrok http 3847
```
