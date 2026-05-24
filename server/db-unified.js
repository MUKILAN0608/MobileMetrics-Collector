/**
 * One laptop table: telemetry_records (all phones, old + new API).
 */

const fs = require('fs');
const path = require('path');

const UNIFIED_TABLE = 'runtime_metrics';

function ensureUnifiedSchema(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS telemetry_records (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      device_slot INTEGER NOT NULL,
      user_label TEXT,
      table_name TEXT NOT NULL,
      record_id INTEGER NOT NULL,
      timestamp INTEGER NOT NULL,
      cpu_usage REAL NOT NULL,
      memory_used INTEGER NOT NULL,
      memory_available INTEGER NOT NULL,
      battery_percentage INTEGER NOT NULL,
      charging_status TEXT NOT NULL,
      device_temperature REAL NOT NULL,
      process_count INTEGER NOT NULL,
      foreground_app TEXT NOT NULL,
      running_apps_count INTEGER NOT NULL,
      runtime_state TEXT NOT NULL,
      received_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_telemetry_device_slot ON telemetry_records(device_slot, timestamp DESC);
    CREATE INDEX IF NOT EXISTS idx_telemetry_received ON telemetry_records(received_at DESC);
  `);

  try {
    db.exec(`ALTER TABLE telemetry_records ADD COLUMN data_source TEXT DEFAULT 'api_v1'`);
  } catch {
    // column exists
  }

  try {
    db.exec(`CREATE INDEX IF NOT EXISTS idx_telemetry_source ON telemetry_records(data_source)`);
  } catch {
    // ignore
  }

  db.prepare(
    `UPDATE telemetry_records SET table_name = ? WHERE table_name LIKE 'device%_runtime_metrics'`,
  ).run(UNIFIED_TABLE);

  db.prepare(
    `UPDATE telemetry_records SET data_source = 'api_v1' WHERE data_source IS NULL OR data_source = ''`,
  ).run();
}

function createInsertStatement(db) {
  return db.prepare(`
    INSERT OR IGNORE INTO telemetry_records (
      device_id, device_slot, user_label, table_name, record_id,
      timestamp, cpu_usage, memory_used, memory_available,
      battery_percentage, charging_status, device_temperature,
      process_count, foreground_app, running_apps_count, runtime_state,
      data_source, received_at
    ) VALUES (
      @device_id, @device_slot, @user_label, @table_name, @record_id,
      @timestamp, @cpu_usage, @memory_used, @memory_available,
      @battery_percentage, @charging_status, @device_temperature,
      @process_count, @foreground_app, @running_apps_count, @runtime_state,
      @data_source, @received_at
    )
  `);
}

function insertTelemetryRows(db, insert, rows, meta) {
  const receivedAt = meta.receivedAt ?? Date.now();
  const deviceId = meta.deviceId;
  const deviceSlot = meta.deviceSlot;
  const userLabel = meta.userLabel ?? '';
  const dataSource = meta.dataSource ?? 'api_v1';
  let inserted = 0;
  let ignored = 0;

  const tx = db.transaction((list) => {
    for (const r of list) {
      const info = insert.run({
        device_id: deviceId,
        device_slot: deviceSlot,
        user_label: userLabel,
        table_name: UNIFIED_TABLE,
        record_id: Number(r.record_id),
        timestamp: Number(r.timestamp),
        cpu_usage: Number(r.cpu_usage),
        memory_used: Number(r.memory_used),
        memory_available: Number(r.memory_available),
        battery_percentage: Number(r.battery_percentage),
        charging_status: String(r.charging_status || ''),
        device_temperature: Number(r.device_temperature),
        process_count: Number(r.process_count),
        foreground_app: String(r.foreground_app || ''),
        running_apps_count: Number(r.running_apps_count),
        runtime_state: String(r.runtime_state || ''),
        data_source: dataSource,
        received_at: receivedAt,
      });
      if (info.changes > 0) inserted++;
      else ignored++;
    }
  });

  tx(rows);
  return { inserted, ignored };
}

function tableExists(db, name) {
  const row = db
    .prepare(`SELECT 1 FROM sqlite_master WHERE type='table' AND name=?`)
    .get(name);
  return !!row;
}

function migrateLegacyDeviceTables(db, insert) {
  const legacy = [
    { table: 'device1_runtime_metrics', slot: 1 },
    { table: 'device2_runtime_metrics', slot: 2 },
    { table: 'device3_runtime_metrics', slot: 3 },
  ];
  let total = 0;

  for (const { table, slot } of legacy) {
    if (!tableExists(db, table)) continue;
    const rows = db.prepare(`SELECT * FROM ${table}`).all();
    if (!rows.length) continue;
    const { inserted } = insertTelemetryRows(db, insert, rows, {
      deviceId: 'legacy-migration',
      deviceSlot: slot,
      userLabel: `User ${slot}`,
      dataSource: 'legacy_local_table',
    });
    total += inserted;
    console.log(`Migrated ${inserted} rows from ${table} -> telemetry_records`);
  }
  return total;
}

function importAttachedDatabase(db, insert, filePath, label) {
  if (!filePath || !fs.existsSync(filePath)) return 0;
  const abs = path.resolve(filePath).replace(/\\/g, '/');
  const alias = `ext_${label.replace(/\W/g, '_')}`;

  try {
    db.exec(`ATTACH DATABASE '${abs}' AS ${alias}`);
  } catch (e) {
    console.warn(`Could not attach ${filePath}:`, e.message);
    return 0;
  }

  let total = 0;

  if (tableExists(db, `${alias}.telemetry_records`)) {
    const rows = db.prepare(`SELECT * FROM ${alias}.telemetry_records`).all();
    for (const row of rows) {
      const info = insert.run({
        device_id: row.device_id || `import-${label}`,
        device_slot: row.device_slot ?? 1,
        user_label: row.user_label || '',
        table_name: UNIFIED_TABLE,
        record_id: row.record_id,
        timestamp: row.timestamp,
        cpu_usage: row.cpu_usage,
        memory_used: row.memory_used,
        memory_available: row.memory_available,
        battery_percentage: row.battery_percentage,
        charging_status: row.charging_status,
        device_temperature: row.device_temperature,
        process_count: row.process_count,
        foreground_app: row.foreground_app,
        running_apps_count: row.running_apps_count,
        runtime_state: row.runtime_state,
        data_source: row.data_source || `import_${label}`,
        received_at: row.received_at || Date.now(),
      });
      if (info.changes > 0) total++;
    }
    console.log(`Imported ${total} rows from ${filePath} (telemetry_records)`);
  }

  for (const { table, slot } of [
    { table: 'device1_runtime_metrics', slot: 1 },
    { table: 'device2_runtime_metrics', slot: 2 },
    { table: 'device3_runtime_metrics', slot: 3 },
  ]) {
    if (!tableExists(db, `${alias}.${table}`)) continue;
    const rows = db.prepare(`SELECT * FROM ${alias}.${table}`).all();
    const { inserted } = insertTelemetryRows(db, insert, rows, {
      deviceId: `import-${label}`,
      deviceSlot: slot,
      dataSource: `import_${label}_${table}`,
    });
    total += inserted;
    console.log(`Imported ${inserted} from ${filePath} (${table})`);
  }

  try {
    db.exec(`DETACH DATABASE ${alias}`);
  } catch {
    // ignore
  }

  return total;
}

function runStartupMigrations(db, serverDir, repoRoot) {
  ensureUnifiedSchema(db);
  const insert = createInsertStatement(db);
  let migrated = 0;

  migrated += migrateLegacyDeviceTables(db, insert);

  const candidates = [
    path.join(serverDir, 'telemetry-server-old.db'),
    path.join(repoRoot, 'telemetry.db'),
    path.join(repoRoot, 'telemetry-live.db'),
  ];

  for (const file of candidates) {
    migrated += importAttachedDatabase(db, insert, file, path.basename(file));
  }

  if (migrated > 0) {
    console.log(`Unified DB: migrated/imported ${migrated} rows into telemetry_records`);
  }

  return insert;
}

module.exports = {
  UNIFIED_TABLE,
  ensureUnifiedSchema,
  createInsertStatement,
  insertTelemetryRows,
  runStartupMigrations,
};
