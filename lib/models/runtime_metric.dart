class RuntimeMetric {
  final int recordId;
  final int timestamp;
  final double cpuUsage;
  final int memoryUsed;
  final int memoryAvailable;
  final int batteryPercentage;
  final String chargingStatus;
  final double deviceTemperature;
  final int processCount;
  final String foregroundApp;
  final int runningAppsCount;
  final String runtimeState;

  const RuntimeMetric({
    required this.recordId,
    required this.timestamp,
    required this.cpuUsage,
    required this.memoryUsed,
    required this.memoryAvailable,
    required this.batteryPercentage,
    required this.chargingStatus,
    required this.deviceTemperature,
    required this.processCount,
    required this.foregroundApp,
    required this.runningAppsCount,
    required this.runtimeState,
  });

  factory RuntimeMetric.fromMap(Map<dynamic, dynamic> map) {
    return RuntimeMetric(
      recordId: _asInt(map['record_id']),
      timestamp: _asInt(map['timestamp']),
      cpuUsage: _asDouble(map['cpu_usage']),
      memoryUsed: _asInt(map['memory_used']),
      memoryAvailable: _asInt(map['memory_available']),
      batteryPercentage: _asInt(map['battery_percentage']),
      chargingStatus: map['charging_status']?.toString() ?? 'UNKNOWN',
      deviceTemperature: _asDouble(map['device_temperature']),
      processCount: _asInt(map['process_count']),
      foregroundApp: map['foreground_app']?.toString() ?? 'Unknown',
      runningAppsCount: _asInt(map['running_apps_count']),
      runtimeState: map['runtime_state']?.toString() ?? 'NORMAL',
    );
  }

  double get memoryUsedMb => memoryUsed / (1024 * 1024);
  double get memoryAvailableMb => memoryAvailable / (1024 * 1024);
}

class LiveTelemetrySnapshot {
  final String timestamp;
  final double cpuUsage;
  final double memoryUsedMb;
  final double memoryAvailableMb;
  final double memoryUsedPercent;
  final int batteryPercentage;
  final String chargingStatus;
  final double deviceTemperature;
  final int processCount;
  final String foregroundApp;
  final int runningAppsCount;
  final String runtimeState;
  final int assignedDevice;
  final int recordsCollectedSession;
  final String tableName;
  final int appSwitchesInInterval;
  final bool dbSaved;
  final int dbLastRecordId;
  final int dbTotalDevice1;
  final int dbTotalDevice2;
  final int dbTotalDevice3;
  final String? dbError;
  final bool? apiSyncSuccess;
  final bool apiSyncSkipped;
  final int apiSyncInserted;
  final String? apiSyncError;

  const LiveTelemetrySnapshot({
    required this.timestamp,
    required this.cpuUsage,
    required this.memoryUsedMb,
    required this.memoryAvailableMb,
    required this.memoryUsedPercent,
    required this.batteryPercentage,
    required this.chargingStatus,
    required this.deviceTemperature,
    required this.processCount,
    required this.foregroundApp,
    required this.runningAppsCount,
    required this.runtimeState,
    required this.assignedDevice,
    required this.recordsCollectedSession,
    required this.tableName,
    required this.appSwitchesInInterval,
    this.dbSaved = false,
    this.dbLastRecordId = -1,
    this.dbTotalDevice1 = 0,
    this.dbTotalDevice2 = 0,
    this.dbTotalDevice3 = 0,
    this.dbError,
    this.apiSyncSuccess,
    this.apiSyncSkipped = false,
    this.apiSyncInserted = 0,
    this.apiSyncError,
  });

  factory LiveTelemetrySnapshot.fromMap(Map<dynamic, dynamic> map) {
    return LiveTelemetrySnapshot(
      timestamp: map['timestamp']?.toString() ?? '',
      cpuUsage: _asDouble(map['cpu_usage']),
      memoryUsedMb: _asDouble(map['memory_used_mb']),
      memoryAvailableMb: _asDouble(map['memory_available_mb']),
      memoryUsedPercent: _asDouble(map['memory_used_percent']),
      batteryPercentage: _asInt(map['battery_percentage']),
      chargingStatus: map['charging_status']?.toString() ?? 'UNKNOWN',
      deviceTemperature: _asDouble(map['device_temperature']),
      processCount: _asInt(map['process_count']),
      foregroundApp: map['foreground_app']?.toString() ?? 'Unknown',
      runningAppsCount: _asInt(map['running_apps_count']),
      runtimeState: map['runtime_state']?.toString() ?? 'NORMAL',
      assignedDevice: _asInt(map['assigned_device'], fallback: 1),
      recordsCollectedSession: _asInt(map['records_collected_session']),
      tableName: map['table_name']?.toString() ?? 'device1_runtime_metrics',
      appSwitchesInInterval: _asInt(map['app_switches_in_interval']),
      dbSaved: map['db_saved'] == true,
      dbLastRecordId: _asInt(map['db_last_record_id'], fallback: -1),
      dbTotalDevice1: _asInt(map['db_total_device1']),
      dbTotalDevice2: _asInt(map['db_total_device2']),
      dbTotalDevice3: _asInt(map['db_total_device3']),
      dbError: map['db_error']?.toString(),
      apiSyncSuccess: _apiSuccess(map['api_sync']),
      apiSyncSkipped: map['api_sync'] is Map && map['api_sync']['skipped'] == true,
      apiSyncInserted: _apiInserted(map['api_sync']),
      apiSyncError: _apiError(map['api_sync']),
    );
  }

  /// Builds live UI from the latest row read directly from on-device SQLite.
  factory LiveTelemetrySnapshot.fromRuntimeMetric(
    RuntimeMetric metric, {
    required int assignedDevice,
    required Map<String, int> counts,
    required bool isolatedMode,
  }) {
    final t = DateTime.fromMillisecondsSinceEpoch(metric.timestamp);
    final ts = '${t.year}-${_pad(t.month)}-${_pad(t.day)} '
        '${_pad(t.hour)}:${_pad(t.minute)}:${_pad(t.second)}';
    final totalMem = metric.memoryUsed + metric.memoryAvailable;
    final memPct = totalMem > 0 ? (metric.memoryUsed / totalMem) * 100 : 0.0;

    return LiveTelemetrySnapshot(
      timestamp: ts,
      cpuUsage: metric.cpuUsage,
      memoryUsedMb: metric.memoryUsedMb,
      memoryAvailableMb: metric.memoryAvailableMb,
      memoryUsedPercent: memPct,
      batteryPercentage: metric.batteryPercentage,
      chargingStatus: metric.chargingStatus,
      deviceTemperature: metric.deviceTemperature,
      processCount: metric.processCount,
      foregroundApp: metric.foregroundApp,
      runningAppsCount: metric.runningAppsCount,
      runtimeState: metric.runtimeState,
      assignedDevice: assignedDevice,
      recordsCollectedSession: counts['device$assignedDevice'] ?? 0,
      tableName: 'device${assignedDevice}_runtime_metrics',
      appSwitchesInInterval: 0,
      dbSaved: true,
      dbLastRecordId: metric.recordId,
      dbTotalDevice1: counts['device1'] ?? 0,
      dbTotalDevice2: counts['device2'] ?? 0,
      dbTotalDevice3: counts['device3'] ?? 0,
    );
  }
}

String _pad(int n) => n.toString().padLeft(2, '0');

class RuntimeStateDistribution {
  final String runtimeState;
  final int count;

  const RuntimeStateDistribution({
    required this.runtimeState,
    required this.count,
  });

  factory RuntimeStateDistribution.fromMap(Map<dynamic, dynamic> map) {
    return RuntimeStateDistribution(
      runtimeState: map['runtime_state']?.toString() ?? 'NORMAL',
      count: _asInt(map['count']),
    );
  }
}

int _asInt(dynamic value, {int fallback = 0}) {
  if (value is int) return value;
  if (value is double) return value.round();
  return int.tryParse(value?.toString() ?? '') ?? fallback;
}

double _asDouble(dynamic value) {
  if (value is double) return value;
  if (value is int) return value.toDouble();
  return double.tryParse(value?.toString() ?? '') ?? 0;
}

bool? _apiSuccess(dynamic apiSync) {
  if (apiSync is! Map) return null;
  return apiSync['success'] == true;
}

int _apiInserted(dynamic apiSync) {
  if (apiSync is! Map) return 0;
  return _asInt(apiSync['inserted']);
}

String? _apiError(dynamic apiSync) {
  if (apiSync is! Map) return null;
  final err = apiSync['error']?.toString();
  if (err != null && err.isNotEmpty) return err;
  return null;
}
