# Constant API URL (team)

## Single source of truth

Edit **one file**:

```
config/api-endpoint.txt
```

Then apply to the whole project:

```powershell
cd C:\Users\Mukil\appfabricx
.\scripts\apply-api-endpoint.ps1
flutter build apk --debug
```

Share the new APK with teammates.

## When ngrok restarts (free tier)

Free ngrok changes the URL each time. After `ngrok http 3847`:

```powershell
.\scripts\sync-ngrok-url.ps1
flutter build apk --debug
```

This updates `api-endpoint.txt` + app + sends URL to USB phone.

## Truly permanent URL (never changes)

**Option A — ngrok reserved domain (paid)**  
1. Reserve domain in ngrok dashboard  
2. Edit `ngrok.yml` → uncomment `domain:`  
3. Run: `ngrok start appfabric`  
4. Put that URL in `config/api-endpoint.txt` → `apply-api-endpoint.ps1` → rebuild APK  

**Option B — Cloud API (free, stable)**  
Deploy `server/` to Render/Railway; use that `https://....onrender.com` URL in `api-endpoint.txt`.

## App behavior

- Every launch **forces** the URL from `TelemetryApiDefaults.DEFAULT_BASE_URL`  
- Old saved URLs in phone storage are **ignored**  
- All teammates must use the **same APK** built after `apply-api-endpoint.ps1`
