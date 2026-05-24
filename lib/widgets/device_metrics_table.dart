import 'package:flutter/material.dart';

import '../models/runtime_metric.dart';

/// Displays all 12 DB columns for one isolated user/device table.
class DeviceMetricsTable extends StatelessWidget {
  final int deviceNumber;
  final String tableName;
  final int recordCount;
  final List<RuntimeMetric> rows;
  final bool isActiveWriter;

  const DeviceMetricsTable({
    super.key,
    required this.deviceNumber,
    required this.tableName,
    required this.recordCount,
    required this.rows,
    this.isActiveWriter = false,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  'User / Device $deviceNumber',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                const SizedBox(width: 8),
                if (isActiveWriter)
                  const Chip(
                    label: Text('ISOLATED WRITE', style: TextStyle(fontSize: 10)),
                    backgroundColor: Colors.teal,
                    labelStyle: TextStyle(color: Colors.white),
                    visualDensity: VisualDensity.compact,
                  ),
                const Spacer(),
                Text('$recordCount rows', style: const TextStyle(color: Colors.grey)),
              ],
            ),
            Text(tableName, style: const TextStyle(fontSize: 11, color: Colors.grey)),
            const SizedBox(height: 8),
            if (rows.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  'No records — assign this phone as User $deviceNumber or wait for telemetry.',
                ),
              )
            else
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: DataTable(
                  headingRowHeight: 40,
                  dataRowMinHeight: 36,
                  dataRowMaxHeight: 56,
                  columns: const [
                    DataColumn(label: Text('record_id')),
                    DataColumn(label: Text('timestamp')),
                    DataColumn(label: Text('cpu_usage')),
                    DataColumn(label: Text('memory_used')),
                    DataColumn(label: Text('memory_avail')),
                    DataColumn(label: Text('battery_%')),
                    DataColumn(label: Text('charging')),
                    DataColumn(label: Text('temp_C')),
                    DataColumn(label: Text('processes')),
                    DataColumn(label: Text('foreground_app')),
                    DataColumn(label: Text('running_apps')),
                    DataColumn(label: Text('runtime_state')),
                  ],
                  rows: rows.map(_buildRow).toList(),
                ),
              ),
          ],
        ),
      ),
    );
  }

  DataRow _buildRow(RuntimeMetric m) {
    final t = DateTime.fromMillisecondsSinceEpoch(m.timestamp);
    final timeStr = '${t.year}-${_pad(t.month)}-${_pad(t.day)} '
        '${_pad(t.hour)}:${_pad(t.minute)}:${_pad(t.second)}';
    final app = m.foregroundApp.length > 24
        ? '${m.foregroundApp.substring(0, 24)}…'
        : m.foregroundApp;

    return DataRow(cells: [
      DataCell(Text('${m.recordId}')),
      DataCell(Text(timeStr, style: const TextStyle(fontSize: 11))),
      DataCell(Text(m.cpuUsage.toStringAsFixed(1))),
      DataCell(Text('${m.memoryUsed}')),
      DataCell(Text('${m.memoryAvailable}')),
      DataCell(Text('${m.batteryPercentage}')),
      DataCell(Text(m.chargingStatus)),
      DataCell(Text(m.deviceTemperature.toStringAsFixed(1))),
      DataCell(Text('${m.processCount}')),
      DataCell(Text(app)),
      DataCell(Text('${m.runningAppsCount}')),
      DataCell(Text(m.runtimeState)),
    ]);
  }

  String _pad(int n) => n.toString().padLeft(2, '0');
}
