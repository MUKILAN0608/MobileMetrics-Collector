import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../config/telemetry_api_defaults.dart';
import '../models/all_devices_tables.dart';
import '../models/runtime_metric.dart';
import '../services/telemetry_service.dart';
import '../widgets/device_metrics_table.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('appfabric/channel');

  final TelemetryService _telemetryService = TelemetryService();

  LiveTelemetrySnapshot? _liveSnapshot;
  RuntimeMetric? _latestDbRow;
  DateTime? _lastUiRefresh;
  String _updateSource = 'on-device database';
  Map<String, int> _recordCounts = {};
  List<RuntimeStateDistribution> _stateDistribution = [];
  AllDevicesTables _allDevicesTables = const AllDevicesTables(
    counts: {},
    device1: [],
    device2: [],
    device3: [],
    exportPath: '',
  );

  int _assignedDevice = 1;
  int _viewDevice = 1;
  bool _loading = true;
  bool _hasUsageAccess = false;
  bool _mirrorToAllTables = false;
  String _exportPath = '';
  bool _refreshingAnalytics = false;
  Timer? _refreshTimer;
  ApiSyncStatus _apiSyncStatus = const ApiSyncStatus(
    enabled: false,
    baseUrl: '',
    deviceId: '',
    hasApiKey: false,
    lastSyncAtMs: 0,
    lastSyncError: '',
    lastInserted: 0,
  );
  final TextEditingController _apiUrlController = TextEditingController();
  final TextEditingController _apiKeyController = TextEditingController();
  String? _apiTestMessage;
  bool _apiConnecting = false;
  String _apiConnectionLabel = 'Checking…';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
    _channel.setMethodCallHandler(_onNativeCall);
    // On-device DB poll — works without USB / laptop.
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      _refreshAnalytics();
      _loadApiSyncStatus();
    });
    Timer.periodic(const Duration(seconds: 30), (_) {
      if (_apiSyncStatus.lastSyncError.isNotEmpty || !_apiSyncStatus.enabled) {
        _connectApiOnLaunch();
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshAnalytics();
      _telemetryService.startMonitoring();
      _telemetryService.syncPendingToApi();
      _loadApiSyncStatus();
    }
  }

  LiveTelemetrySnapshot? get _effectiveLive {
    if (_liveSnapshot != null) return _liveSnapshot;
    if (_latestDbRow == null) return null;
    return LiveTelemetrySnapshot.fromRuntimeMetric(
      _latestDbRow!,
      assignedDevice: _assignedDevice,
      counts: _recordCounts,
      isolatedMode: !_mirrorToAllTables,
    );
  }

  List<RuntimeMetric> _rowsForDevice(int device) {
    switch (device) {
      case 1:
        return _allDevicesTables.device1;
      case 2:
        return _allDevicesTables.device2;
      default:
        return _allDevicesTables.device3;
    }
  }

  Future<void> _init() async {
    _assignedDevice = await _telemetryService.getDeviceAssignment();
    _viewDevice = _assignedDevice;
    _hasUsageAccess = await _telemetryService.hasUsageAccess();
    _mirrorToAllTables = await _telemetryService.isMirrorToAllTables();
    _exportPath = await _telemetryService.getExportPath();
    await _telemetryService.verifyDatabase();
    await _connectApiOnLaunch();
    await _refreshAnalytics();
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _connectApiOnLaunch() async {
    setState(() {
      _apiConnecting = true;
      _apiConnectionLabel = 'Connecting to ngrok API…';
    });
    try {
      final result = await _telemetryService.ensureApiConfigured();
      final statusMap = result['status'];
      if (statusMap is Map) {
        _apiSyncStatus = ApiSyncStatus.fromMap(statusMap);
      } else {
        await _loadApiSyncStatus();
      }
      final test = result['test'];
      final sync = result['sync'];
      if (test is Map && test['success'] == true) {
        _apiConnectionLabel = 'Cloud API connected';
        _apiTestMessage = 'Auto-discovered ngrok URL';
        if (sync is Map && (sync['inserted'] as num? ?? 0) > 0) {
          _apiTestMessage = 'Synced ${sync['inserted']} rows to laptop';
        }
      } else {
        final err = test is Map
            ? test['error']?.toString()
            : result['error']?.toString();
        _apiConnectionLabel = 'Reconnecting…';
        _apiTestMessage = err ?? 'Ensure ngrok http 3847 + API on laptop';
      }
      _apiUrlController.text = TelemetryApiDefaults.defaultBaseUrl;
    } catch (e) {
      _apiConnectionLabel = 'Cloud API error';
      _apiTestMessage = e.toString();
    } finally {
      if (mounted) {
        setState(() => _apiConnecting = false);
      }
    }
  }

  Future<void> _loadApiSyncStatus() async {
    final status = await _telemetryService.getApiSyncStatus();
    if (!mounted) return;
    setState(() {
      _apiSyncStatus = status;
      if (status.baseUrl.isNotEmpty) {
        _apiUrlController.text = status.baseUrl;
      }
      _updateApiConnectionLabel();
    });
  }

  void _updateApiConnectionLabel() {
    if (!_apiSyncStatus.enabled) {
      _apiConnectionLabel = 'API not configured';
      return;
    }
    if (_apiSyncStatus.lastSyncError.isNotEmpty) {
      _apiConnectionLabel = 'Upload error';
      return;
    }
    if (_apiSyncStatus.lastSyncAtMs > 0) {
      _apiConnectionLabel = 'Uploading to laptop';
      return;
    }
    _apiConnectionLabel = 'API ready';
  }

  Future<void> _applyApiSyncPayload(Map<dynamic, dynamic> payload) async {
    final success = payload['success'] == true;
    final skipped = payload['skipped'] == true;
    final inserted = payload['inserted'] is int
        ? payload['inserted'] as int
        : int.tryParse('${payload['inserted']}') ?? 0;
    final error = payload['error']?.toString() ?? '';

    if (!skipped) {
      if (success) {
        _apiConnectionLabel = inserted > 0 ? 'Uploaded +$inserted' : 'Cloud sync OK';
        _apiTestMessage = null;
      } else if (error.isNotEmpty) {
        _apiConnectionLabel = 'Upload failed';
        _apiTestMessage = error;
      }
    }
    await _loadApiSyncStatus();
  }

  Future<void> _checkUsageAccess() async {
    final granted = await _telemetryService.hasUsageAccess();
    if (mounted && granted != _hasUsageAccess) {
      setState(() => _hasUsageAccess = granted);
    }
  }

  Future<dynamic> _onNativeCall(MethodCall call) async {
    if (call.method == 'apiSyncUpdate' && call.arguments is Map) {
      _applyApiSyncPayload(Map<dynamic, dynamic>.from(call.arguments as Map));
      return;
    }
    if (call.method == 'updateTelemetry' && call.arguments is Map) {
      final snap = LiveTelemetrySnapshot.fromMap(
        Map<dynamic, dynamic>.from(call.arguments as Map),
      );
      setState(() {
        _liveSnapshot = snap;
        _updateSource = _apiSyncStatus.enabled ? 'live + cloud API' : 'live service';
        if (snap.apiSyncSuccess == true && (snap.apiSyncInserted) > 0) {
          _apiConnectionLabel = 'Uploaded +${snap.apiSyncInserted}';
        } else if (snap.apiSyncError != null) {
          _apiConnectionLabel = 'Upload failed';
          _apiTestMessage = snap.apiSyncError;
        }
        _recordCounts = {
          'device1': snap.dbTotalDevice1,
          'device2': snap.dbTotalDevice2,
          'device3': snap.dbTotalDevice3,
        };
        if (snap.foregroundApp.contains('Usage access not granted')) {
          _hasUsageAccess = false;
        } else if (snap.foregroundApp != 'Unknown') {
          _hasUsageAccess = true;
        }
      });
    }
  }

  Future<void> _refreshAnalytics() async {
    if (_refreshingAnalytics) return;
    _refreshingAnalytics = true;
    try {
      final allTables = await _telemetryService.getAllDevicesTables(limit: 10);
      final distribution = await _telemetryService.getStateDistribution(
        deviceSlot: _viewDevice,
      );
      if (!mounted) return;

      final assignedRows = _rowsForDeviceFromTables(allTables, _assignedDevice);
      final latestRow = assignedRows.isNotEmpty ? assignedRows.first : null;

      setState(() {
        _allDevicesTables = allTables;
        _recordCounts = allTables.counts;
        _stateDistribution = distribution;
        _exportPath = allTables.exportPath;
        _lastUiRefresh = DateTime.now();
        _updateSource = 'on-device database';
        if (latestRow != null) {
          _latestDbRow = latestRow;
        }
        // Keep channel snapshot when present; DB tables always refresh.
      });
    } catch (_) {
      // Ignore transient DB/channel errors; live telemetry still works.
    } finally {
      _refreshingAnalytics = false;
    }
  }

  Future<void> _onDeviceAssignmentChanged(int? value) async {
    if (value == null) return;
    await _telemetryService.setDeviceAssignment(value);
    setState(() {
      _assignedDevice = value;
      _viewDevice = value;
    });
    await _refreshAnalytics();
  }

  Future<void> _onViewDeviceChanged(int? value) async {
    if (value == null) return;
    setState(() => _viewDevice = value);
    await _refreshAnalytics();
  }

  List<RuntimeMetric> _rowsForDeviceFromTables(AllDevicesTables tables, int device) {
    switch (device) {
      case 1:
        return tables.device1;
      case 2:
        return tables.device2;
      default:
        return tables.device3;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _refreshTimer?.cancel();
    _apiUrlController.dispose();
    _apiKeyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final totalRecords = _recordCounts.values.fold<int>(0, (a, b) => a + b);
    final deviceCount = _recordCounts['device$_viewDevice'] ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: const Text('AppFabric X∞ Runtime Telemetry'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: _apiStatusChip(),
            ),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _refreshAnalytics,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _standaloneModeBanner(),
                  const SizedBox(height: 12),
                  if (!_hasUsageAccess) ...[
                    _usageAccessBanner(),
                    const SizedBox(height: 16),
                  ],
                  _sectionTitle('Device Assignment'),
                  _deviceAssignmentCard(),
                  const SizedBox(height: 16),
                  _sectionTitle('Cloud API sync (no USB)'),
                  _apiSyncCard(),
                  const SizedBox(height: 16),
                  _sectionTitle('Live Runtime Feed (Device $_assignedDevice)'),
                  _liveMetricsCard(),
                  const SizedBox(height: 16),
                  _sectionTitle('Dataset Progress'),
                  _datasetProgressCard(totalRecords, deviceCount),
                  const SizedBox(height: 16),
                  _sectionTitle('Runtime State Distribution — Device $_viewDevice'),
                  _stateDistributionCard(),
                  const SizedBox(height: 16),
                  _sectionTitle('Per-User Isolated Tables (all 12 DB columns)'),
                  _mirrorModeSwitch(),
                  if (_exportPath.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(
                        'CSV auto-export: $_exportPath',
                        style: const TextStyle(fontSize: 11, color: Colors.grey),
                      ),
                    ),
                  DeviceMetricsTable(
                    deviceNumber: 1,
                    tableName: 'device1_runtime_metrics',
                    recordCount: _recordCounts['device1'] ?? 0,
                    rows: _rowsForDevice(1),
                    isActiveWriter: _assignedDevice == 1 && !_mirrorToAllTables,
                  ),
                  DeviceMetricsTable(
                    deviceNumber: 2,
                    tableName: 'device2_runtime_metrics',
                    recordCount: _recordCounts['device2'] ?? 0,
                    rows: _rowsForDevice(2),
                    isActiveWriter: _assignedDevice == 2 && !_mirrorToAllTables,
                  ),
                  DeviceMetricsTable(
                    deviceNumber: 3,
                    tableName: 'device3_runtime_metrics',
                    recordCount: _recordCounts['device3'] ?? 0,
                    rows: _rowsForDevice(3),
                    isActiveWriter: _assignedDevice == 3 && !_mirrorToAllTables,
                  ),
                ],
              ),
            ),
    );
  }

  Widget _standaloneModeBanner() {
    final refreshText = _lastUiRefresh == null
        ? 'Starting…'
        : 'Updated ${_lastUiRefresh!.hour.toString().padLeft(2, '0')}:'
            '${_lastUiRefresh!.minute.toString().padLeft(2, '0')}:'
            '${_lastUiRefresh!.second.toString().padLeft(2, '0')}';
    return Card(
      color: Colors.indigo.shade50,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            const Icon(Icons.phone_android, color: Colors.indigo),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _apiSyncStatus.enabled
                        ? 'API sync ON — phone uploads via HTTP'
                        : 'Standalone live mode — no USB required',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  Text(
                    _apiSyncStatus.enabled
                        ? 'Laptop DB updates when server receives POST. $_updateSource. $refreshText'
                        : 'Reads SQLite on this phone every 3s. Source: $_updateSource. $refreshText',
                    style: const TextStyle(fontSize: 12),
                  ),
                ],
              ),
            ),
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ],
        ),
      ),
    );
  }

  Widget _usageAccessBanner() {
    return Card(
      color: Colors.orange.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Usage access required for foreground app tracking',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              'On Samsung: Settings → Apps → appfabricx → '
              'Allow restricted settings (if shown) → then enable Usage access.\n'
              'Or: Settings → Security → Other security settings → Usage data access → appfabricx → ON.',
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () async {
                await _telemetryService.openUsageAccessSettings();
              },
              child: const Text('Open Usage Access Settings'),
            ),
            TextButton(
              onPressed: _checkUsageAccess,
              child: const Text('I enabled it — check again'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
      ),
    );
  }

  Widget _deviceAssignmentCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Each user gets an isolated SQLite table. This phone writes ONLY to the table you select below.',
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              value: _assignedDevice,
              decoration: const InputDecoration(
                labelText: 'This phone = team device',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(
                  value: 1,
                  child: Text('User 1 → device1_runtime_metrics (isolated)'),
                ),
                DropdownMenuItem(
                  value: 2,
                  child: Text('User 2 → device2_runtime_metrics (isolated)'),
                ),
                DropdownMenuItem(
                  value: 3,
                  child: Text('User 3 → device3_runtime_metrics (isolated)'),
                ),
              ],
              onChanged: _onDeviceAssignmentChanged,
            ),
            const SizedBox(height: 8),
            const Text(
              'Phone A = User 1 only | Phone B = User 2 only | Phone C = User 3 only. '
              'All 12 fields (record_id … runtime_state) are stored per row.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }

  Widget _apiStatusChip() {
    Color bg;
    if (_apiConnecting) {
      bg = Colors.blueGrey;
    } else if (_apiSyncStatus.lastSyncError.isNotEmpty) {
      bg = Colors.red;
    } else if (_apiSyncStatus.enabled && _apiSyncStatus.lastSyncAtMs > 0) {
      bg = Colors.teal;
    } else if (_apiSyncStatus.enabled) {
      bg = Colors.orange;
    } else {
      bg = Colors.grey;
    }
    return Chip(
      label: Text(
        _apiConnecting ? 'API…' : _apiConnectionLabel,
        style: const TextStyle(color: Colors.white, fontSize: 10),
      ),
      backgroundColor: bg,
      padding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
    );
  }

  Widget _apiSyncCard() {
    final lastSync = _apiSyncStatus.lastSyncAtMs > 0
        ? DateTime.fromMillisecondsSinceEpoch(_apiSyncStatus.lastSyncAtMs)
        : null;
    final syncLabel = lastSync == null
        ? 'Never synced'
        : 'Last sync ${lastSync.hour.toString().padLeft(2, '0')}:'
            '${lastSync.minute.toString().padLeft(2, '0')}:'
            '${lastSync.second.toString().padLeft(2, '0')} '
            '(+${_apiSyncStatus.lastInserted} rows)';

    return Card(
      color: Colors.teal.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _apiSyncStatus.lastSyncError.isEmpty && _apiSyncStatus.enabled
                      ? Icons.cloud_done
                      : Icons.cloud_sync,
                  color: _apiSyncStatus.lastSyncError.isEmpty ? Colors.teal : Colors.orange,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Auto-sync to laptop via ngrok (no USB). URL is discovered automatically.',
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            if (_apiSyncStatus.baseUrl.isNotEmpty) ...[
              const SizedBox(height: 8),
              SelectableText(
                'Active: ${_apiSyncStatus.baseUrl}',
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ],
            const SizedBox(height: 12),
            ExpansionTile(
              title: const Text('Advanced API settings', style: TextStyle(fontSize: 13)),
              children: [
                SelectableText(
                  'Constant URL: ${TelemetryApiDefaults.defaultBaseUrl}',
                  style: const TextStyle(fontSize: 11),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _apiUrlController,
                  readOnly: true,
                  decoration: const InputDecoration(
                    labelText: 'API URL (fixed in APK)',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _apiKeyController,
                  decoration: const InputDecoration(
                    labelText: 'API key (optional)',
                    border: OutlineInputBorder(),
                  ),
                  obscureText: true,
                ),
              ],
            ),
            if (_apiSyncStatus.deviceId.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'Device ID: ${_apiSyncStatus.deviceId}',
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton(
                  onPressed: _apiConnecting
                      ? null
                      : () async {
                          await _telemetryService.configureApi(
                            baseUrl: TelemetryApiDefaults.defaultBaseUrl,
                            apiKey: _apiKeyController.text.trim(),
                          );
                          await _connectApiOnLaunch();
                        },
                  child: const Text('Reconnect now'),
                ),
                OutlinedButton(
                  onPressed: _apiConnecting
                      ? null
                      : () async {
                          final result =
                              await _telemetryService.testApiConnection();
                          setState(() {
                            _apiTestMessage = result['success'] == true
                                ? (result['message']?.toString() ?? 'OK')
                                : (result['error']?.toString() ?? 'Failed');
                          });
                        },
                  child: const Text('Test'),
                ),
                OutlinedButton(
                  onPressed: () async {
                    await _telemetryService.syncPendingToApi();
                    await _loadApiSyncStatus();
                  },
                  child: const Text('Sync now'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              _apiSyncStatus.enabled ? syncLabel : 'API URL not set',
              style: TextStyle(
                fontSize: 12,
                color: _apiSyncStatus.lastSyncError.isNotEmpty
                    ? Colors.red
                    : Colors.green.shade800,
              ),
            ),
            if (_apiSyncStatus.lastSyncError.isNotEmpty)
              Text(
                'Error: ${_apiSyncStatus.lastSyncError}',
                style: const TextStyle(fontSize: 12, color: Colors.red),
              ),
            if (_apiTestMessage != null)
              Text(
                'Test: $_apiTestMessage',
                style: const TextStyle(fontSize: 12),
              ),
          ],
        ),
      ),
    );
  }

  Widget _liveMetricsCard() {
    final snap = _effectiveLive;
    if (snap == null) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Center(
            child: Text(
              'Collecting… Open app once after install. Data saves on-device even offline from laptop.',
            ),
          ),
        ),
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _statusChip(snap.runtimeState),
                const SizedBox(width: 8),
                _dbStatusChip(snap),
                const SizedBox(width: 8),
                _apiUploadChip(snap),
              ],
            ),
            const SizedBox(height: 8),
            Text('Saved to: ${snap.tableName}', style: const TextStyle(color: Colors.grey)),
            Text(
              'DB rows — D1: ${snap.dbTotalDevice1} | D2: ${snap.dbTotalDevice2} | D3: ${snap.dbTotalDevice3}'
              '${snap.dbLastRecordId > 0 ? ' | Last ID: ${snap.dbLastRecordId}' : ''}',
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
            if (snap.dbError != null)
              Text('DB error: ${snap.dbError}', style: const TextStyle(color: Colors.red)),
            Text('Timestamp: ${snap.timestamp}'),
            const Divider(),
            _metricRow('CPU Usage', '${snap.cpuUsage.toStringAsFixed(1)}%'),
            _metricRow('Memory', '${snap.memoryUsedMb.toStringAsFixed(0)} MB used / ${snap.memoryAvailableMb.toStringAsFixed(0)} MB free (${snap.memoryUsedPercent.toStringAsFixed(0)}%)'),
            _metricRow('Battery', '${snap.batteryPercentage}% (${snap.chargingStatus})'),
            _metricRow('Temperature', '${snap.deviceTemperature.toStringAsFixed(1)} °C'),
            _metricRow('Processes', '${snap.processCount}'),
            _metricRow('Running Apps', '${snap.runningAppsCount}'),
            _metricRow('Foreground App', snap.foregroundApp),
            _metricRow('App Switches (interval)', '${snap.appSwitchesInInterval}'),
            _metricRow('Session Records', '${snap.recordsCollectedSession}'),
          ],
        ),
      ),
    );
  }

  Widget _datasetProgressCard(int totalRecords, int deviceCount) {
    const targetMin = 50000;
    final progress = (totalRecords / targetMin).clamp(0.0, 1.0);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Total records (all devices): $totalRecords'),
            Text('Device $_viewDevice records: $deviceCount'),
            const SizedBox(height: 8),
            LinearProgressIndicator(value: progress),
            const SizedBox(height: 4),
            Text(
              'Target: 50,000 – 100,000+ records (${(progress * 100).toStringAsFixed(1)}% of 50k minimum)',
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                _countChip('D1', _recordCounts['device1'] ?? 0),
                const SizedBox(width: 8),
                _countChip('D2', _recordCounts['device2'] ?? 0),
                const SizedBox(width: 8),
                _countChip('D3', _recordCounts['device3'] ?? 0),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _countChip(String label, int count) {
    return Chip(label: Text('$label: $count'));
  }

  Widget _stateDistributionCard() {
    if (_stateDistribution.isEmpty) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text('No runtime state data yet. Keep the app running in background.'),
        ),
      );
    }

    final total = _stateDistribution.fold<int>(0, (sum, e) => sum + e.count);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: _stateDistribution.map((entry) {
            final pct = total > 0 ? (entry.count / total * 100) : 0.0;
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                children: [
                  Expanded(flex: 2, child: Text(entry.runtimeState)),
                  Expanded(
                    flex: 3,
                    child: LinearProgressIndicator(value: pct / 100),
                  ),
                  const SizedBox(width: 8),
                  Text('${entry.count} (${pct.toStringAsFixed(0)}%)'),
                ],
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _mirrorModeSwitch() {
    return Card(
      color: _mirrorToAllTables ? Colors.orange.shade50 : null,
      child: SwitchListTile(
        title: const Text('Disable isolation (demo only)'),
        subtitle: Text(
          _mirrorToAllTables
              ? 'WARNING: Writing to all 3 tables — not isolated per user.'
              : 'ON = isolated (recommended). Each user table only gets their device data.',
        ),
        value: !_mirrorToAllTables,
        onChanged: (isolatedEnabled) async {
          final mirror = !isolatedEnabled;
          await _telemetryService.setMirrorToAllTables(mirror);
          setState(() => _mirrorToAllTables = mirror);
          await _refreshAnalytics();
        },
      ),
    );
  }

  Widget _metricRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 140, child: Text(label, style: const TextStyle(fontWeight: FontWeight.w500))),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }

  Widget _dbStatusChip(LiveTelemetrySnapshot snap) {
    return Chip(
      label: Text(
        snap.dbSaved ? 'DB SAVED' : 'DB FAILED',
        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
      ),
      backgroundColor: snap.dbSaved ? Colors.teal : Colors.red,
    );
  }

  Widget _apiUploadChip(LiveTelemetrySnapshot snap) {
    if (!_apiSyncStatus.enabled) {
      return const Chip(
        label: Text('API OFF', style: TextStyle(color: Colors.white, fontSize: 11)),
        backgroundColor: Colors.grey,
      );
    }
    if (snap.apiSyncSkipped) {
      return const Chip(
        label: Text('API …', style: TextStyle(color: Colors.white, fontSize: 11)),
        backgroundColor: Colors.blueGrey,
      );
    }
    if (snap.apiSyncError != null) {
      return const Chip(
        label: Text('API ERR', style: TextStyle(color: Colors.white, fontSize: 11)),
        backgroundColor: Colors.red,
      );
    }
    if (snap.apiSyncSuccess == true) {
      final label = snap.apiSyncInserted > 0
          ? 'CLOUD +${snap.apiSyncInserted}'
          : 'CLOUD OK';
      return Chip(
        label: Text(label, style: const TextStyle(color: Colors.white, fontSize: 11)),
        backgroundColor: Colors.indigo,
      );
    }
    return const Chip(
      label: Text('CLOUD …', style: TextStyle(color: Colors.white, fontSize: 11)),
      backgroundColor: Colors.blueGrey,
    );
  }

  Widget _statusChip(String state) {
    Color color;
    switch (state) {
      case 'HIGH_LOAD':
      case 'THERMAL_RISK':
        color = Colors.red;
      case 'BATTERY_SAVER':
        color = Colors.orange;
      case 'GAMING_MODE':
        color = Colors.purple;
      case 'MULTITASKING_STRESS':
        color = Colors.amber;
      default:
        color = Colors.green;
    }

    return Chip(
      label: Text(state, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
      backgroundColor: color,
    );
  }
}
