/**
 * AppFabric X — Telemetry API (run on laptop)
 *
 * Phone POSTs metrics over mobile data / Wi‑Fi (no USB).
 * Laptop stores rows in server/telemetry-server.db
 *
 * Start: npm install && npm start
 * Default: http://0.0.0.0:3847
 */

const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');

const PORT = Number(process.env.PORT || 3847);
const API_KEY = process.env.API_KEY || '';
const DB_PATH = path.join(__dirname, 'telemetry-server.db');
const NGROK_URL_FILE = path.join(__dirname, 'ngrok-url.json');

function readNgrokPublicUrl() {
  try {
    const data = JSON.parse(fs.readFileSync(NGROK_URL_FILE, 'utf8'));
    return data.public_url || null;
  } catch {
    return process.env.NGROK_PUBLIC_URL || null;
  }
}

function writeNgrokPublicUrl(publicUrl) {
  if (!publicUrl) return;
  fs.writeFileSync(
    NGROK_URL_FILE,
    JSON.stringify({ public_url: publicUrl, updated_at: new Date().toISOString() }, null, 2),
  );
}

async function pollNgrokAgent() {
  try {
    const res = await fetch('http://127.0.0.1:4040/api/tunnels');
    if (!res.ok) return;
    const data = await res.json();
    const tunnel = (data.tunnels || []).find((t) => t.proto === 'https');
    if (tunnel?.public_url) writeNgrokPublicUrl(tunnel.public_url);
  } catch {
    // ngrok not running
  }
}

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

const {
  UNIFIED_TABLE,
  insertTelemetryRows,
  runStartupMigrations,
} = require('./db-unified');

const insertTelemetry = runStartupMigrations(db, __dirname, path.join(__dirname, '..'));

function requireApiKey(req, res, next) {
  if (!API_KEY) return next();
  const key = req.header('x-api-key') || req.query.api_key;
  if (key !== API_KEY) {
    return res.status(401).json({ ok: false, error: 'Invalid API key' });
  }
  next();
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'appfabric-telemetry-api', port: PORT });
});

/** Phones/laptop read the current ngrok URL (when tunnel is up). */
app.get('/api/v1/bootstrap', (_req, res) => {
  const publicUrl = readNgrokPublicUrl();
  res.json({
    ok: true,
    public_base_url: publicUrl,
    port: PORT,
  });
});

function handleTelemetryUpload(req, res, dataSource) {
  const body = req.body || {};
  const deviceId = String(body.device_id || '').trim();
  const deviceSlot = Number(body.device_slot || 1);
  const userLabel = String(body.user_label || '');
  const records = Array.isArray(body.records) ? body.records : [];

  if (!deviceId) {
    return res.status(400).json({ ok: false, error: 'device_id required' });
  }
  if (!records.length) {
    return res.status(400).json({ ok: false, error: 'records array required' });
  }

  try {
    const { inserted, ignored } = insertTelemetryRows(db, insertTelemetry, records, {
      deviceId,
      deviceSlot,
      userLabel,
      dataSource,
      receivedAt: Date.now(),
    });

    res.json({
      ok: true,
      inserted,
      ignored,
      unified_table: UNIFIED_TABLE,
      total_server_rows: db.prepare('SELECT COUNT(*) AS c FROM telemetry_records').get().c,
    });
  } catch (e) {
    res.status(400).json({ ok: false, error: e.message });
  }
}

/** POST /api/v1/telemetry — batch upload from phone (new API) */
app.post('/api/v1/telemetry', requireApiKey, (req, res) => {
  handleTelemetryUpload(req, res, 'api_v1');
});

/** Legacy paths — same unified table */
app.post('/api/telemetry', requireApiKey, (req, res) => {
  handleTelemetryUpload(req, res, 'api_legacy');
});

app.post('/telemetry', requireApiKey, (req, res) => {
  handleTelemetryUpload(req, res, 'api_legacy');
});

/** GET /api/v1/telemetry?device_slot=1&limit=50 */
app.get('/api/v1/telemetry', requireApiKey, (req, res) => {
  const deviceSlot = req.query.device_slot ? Number(req.query.device_slot) : null;
  const deviceId = req.query.device_id ? String(req.query.device_id) : null;
  const limit = Math.min(Number(req.query.limit || 50), 500);

  let sql = 'SELECT * FROM telemetry_records WHERE 1=1';
  const params = {};
  if (deviceSlot) {
    sql += ' AND device_slot = @device_slot';
    params.device_slot = deviceSlot;
  }
  if (deviceId) {
    sql += ' AND device_id = @device_id';
    params.device_id = deviceId;
  }
  sql += ' ORDER BY timestamp DESC LIMIT @limit';
  params.limit = limit;

  const rows = db.prepare(sql).all(params);
  res.json({ ok: true, count: rows.length, records: rows });
});

/** GET /api/v1/telemetry/all — everything in one table */
app.get('/api/v1/telemetry/all', requireApiKey, (req, res) => {
  const limit = Math.min(Number(req.query.limit || 100), 1000);
  const rows = db.prepare(`
    SELECT * FROM telemetry_records
    ORDER BY timestamp DESC
    LIMIT ?
  `).all(limit);
  res.json({
    ok: true,
    unified_table: UNIFIED_TABLE,
    count: rows.length,
    records: rows,
  });
});

/** GET /api/v1/stats */
app.get('/api/v1/stats', requireApiKey, (_req, res) => {
  const total = db.prepare('SELECT COUNT(*) AS c FROM telemetry_records').get().c;
  const bySlot = db.prepare(`
    SELECT device_slot, COUNT(*) AS count
    FROM telemetry_records
    GROUP BY device_slot
  `).all();
  const bySource = db.prepare(`
    SELECT data_source, COUNT(*) AS count
    FROM telemetry_records
    GROUP BY data_source
  `).all();
  const latest = db.prepare(`
    SELECT device_id, device_slot, record_id, timestamp, cpu_usage, runtime_state,
           data_source, received_at
    FROM telemetry_records
    ORDER BY received_at DESC
    LIMIT 5
  `).all();
  res.json({
    ok: true,
    unified_table: UNIFIED_TABLE,
    total,
    by_slot: bySlot,
    by_source: bySource,
    latest,
  });
});

pollNgrokAgent();
setInterval(pollNgrokAgent, 15000);

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('AppFabric Telemetry API');
  console.log('=======================');
  console.log(`Listening on http://0.0.0.0:${PORT}`);
  console.log(`Database: ${DB_PATH}`);
  if (API_KEY) console.log('API key: enabled (set X-Api-Key header)');
  console.log('');
  console.log('Same Wi‑Fi (local): http://YOUR_LAN_IP:' + PORT);
  console.log('Remote users: use a public URL (cloud deploy or ngrok) — see docs/TELEMETRY_API.md');
  console.log('');
  console.log('Test: curl http://localhost:' + PORT + '/health');
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error('');
    console.error(`Port ${PORT} is already in use — another copy of this server may be running.`);
    console.error('Options:');
    console.error(`  1) Use it:  curl http://localhost:${PORT}/health`);
    console.error(`  2) Stop it: netstat -ano | findstr :${PORT}   then   taskkill /PID <pid> /F`);
    console.error(`  3) Other port: $env:PORT=3848; npm start`);
    console.error('');
    process.exit(1);
  }
  throw err;
});
