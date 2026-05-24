import 'package:flutter/services.dart';

import '../models/all_devices_tables.dart';
import '../models/runtime_metric.dart';

class TelemetryService {
  static const _channel = MethodChannel('appfabric/channel');

  Future<int> getDeviceAssignment() async {
    final result = await _channel.invokeMethod<int>('getDeviceAssignment');
    return result ?? 1;
  }

  Future<int> setDeviceAssignment(int deviceSlot) async {
    final result = await _channel.invokeMethod<int>(
      'setDeviceAssignment',
      {'device_slot': deviceSlot},
    );
    return result ?? deviceSlot;
  }

  Future<Map<String, int>> getRecordCounts() async {
    final result = await _channel.invokeMethod<Map>('getRecordCounts');
    if (result == null) return {};
    return result.map(
      (key, value) => MapEntry(key.toString(), _asInt(value)),
    );
  }

  Future<List<RuntimeMetric>> getRecentMetrics({
    required int deviceSlot,
    int limit = 50,
  }) async {
    final result = await _channel.invokeMethod<List>(
      'getRecentMetrics',
      {'device_slot': deviceSlot, 'limit': limit},
    );
    if (result == null) return [];
    return result
        .whereType<Map>()
        .map((e) => RuntimeMetric.fromMap(e))
        .toList();
  }

  Future<List<RuntimeStateDistribution>> getStateDistribution({
    required int deviceSlot,
  }) async {
    final result = await _channel.invokeMethod<List>(
      'getStateDistribution',
      {'device_slot': deviceSlot},
    );
    if (result == null) return [];
    return result
        .whereType<Map>()
        .map((e) => RuntimeStateDistribution.fromMap(e))
        .toList();
  }

  Future<void> startMonitoring() async {
    await _channel.invokeMethod('startMonitoring');
  }

  Future<bool> hasUsageAccess() async {
    final result = await _channel.invokeMethod<bool>('hasUsageAccess');
    return result ?? false;
  }

  Future<void> openUsageAccessSettings() async {
    await _channel.invokeMethod('openUsageAccessSettings');
  }

  Future<AllDevicesTables> getAllDevicesTables({int limit = 8}) async {
    final result = await _channel.invokeMethod<Map>(
      'getAllDevicesTables',
      {'limit': limit},
    );
    if (result == null) {
      return const AllDevicesTables(
        counts: {},
        device1: [],
        device2: [],
        device3: [],
        exportPath: '',
      );
    }
    return AllDevicesTables.fromMap(result);
  }

  Future<bool> isMirrorToAllTables() async {
    final result = await _channel.invokeMethod<bool>('isMirrorToAllTables');
    return result ?? false;
  }

  Future<void> setMirrorToAllTables(bool enabled) async {
    await _channel.invokeMethod('setMirrorToAllTables', {'enabled': enabled});
  }

  Future<String> getExportPath() async {
    final result = await _channel.invokeMethod<String>('getExportPath');
    return result ?? '';
  }

  Future<bool> verifyDatabase() async {
    final result = await _channel.invokeMethod<Map>('verifyDatabase');
    return result?['ok'] == true;
  }

  Future<ApiSyncStatus> getApiSyncStatus() async {
    final result = await _channel.invokeMethod<Map>('getApiSyncStatus');
    return ApiSyncStatus.fromMap(result ?? {});
  }

  Future<Map<String, dynamic>> ensureApiConfigured() async {
    final result = await _channel.invokeMethod<Map>('ensureApiConfigured');
    return Map<String, dynamic>.from(result ?? {});
  }

  Future<Map<String, dynamic>> configureApi({
    required String baseUrl,
    String apiKey = '',
  }) async {
    final result = await _channel.invokeMethod<Map>(
      'configureApi',
      {'base_url': baseUrl, 'api_key': apiKey},
    );
    return Map<String, dynamic>.from(result ?? {});
  }

  Future<void> setApiBaseUrl(String baseUrl) async {
    await _channel.invokeMethod('setApiBaseUrl', {'base_url': baseUrl});
  }

  Future<void> setApiKey(String apiKey) async {
    await _channel.invokeMethod('setApiKey', {'api_key': apiKey});
  }

  Future<Map<String, dynamic>> testApiConnection() async {
    final result = await _channel.invokeMethod<Map>('testApiConnection');
    return Map<String, dynamic>.from(result ?? {});
  }

  Future<Map<String, dynamic>> syncPendingToApi() async {
    final result = await _channel.invokeMethod<Map>('syncPendingToApi');
    return Map<String, dynamic>.from(result ?? {});
  }

  int _asInt(dynamic value) {
    if (value is int) return value;
    if (value is double) return value.round();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}

class ApiSyncStatus {
  const ApiSyncStatus({
    required this.enabled,
    required this.baseUrl,
    required this.deviceId,
    required this.hasApiKey,
    required this.lastSyncAtMs,
    required this.lastSyncError,
    required this.lastInserted,
  });

  final bool enabled;
  final String baseUrl;
  final String deviceId;
  final bool hasApiKey;
  final int lastSyncAtMs;
  final String lastSyncError;
  final int lastInserted;

  factory ApiSyncStatus.fromMap(Map<dynamic, dynamic> map) {
    return ApiSyncStatus(
      enabled: map['enabled'] == true,
      baseUrl: map['base_url']?.toString() ?? '',
      deviceId: map['device_id']?.toString() ?? '',
      hasApiKey: map['has_api_key'] == true,
      lastSyncAtMs: _parseInt(map['last_sync_at_ms']),
      lastSyncError: map['last_sync_error']?.toString() ?? '',
      lastInserted: _parseInt(map['last_inserted']),
    );
  }

  static int _parseInt(dynamic value) {
    if (value is int) return value;
    if (value is double) return value.round();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}
