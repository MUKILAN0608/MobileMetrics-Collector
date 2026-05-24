import 'runtime_metric.dart';

class AllDevicesTables {
  final Map<String, int> counts;
  final List<RuntimeMetric> device1;
  final List<RuntimeMetric> device2;
  final List<RuntimeMetric> device3;
  final String exportPath;

  const AllDevicesTables({
    required this.counts,
    required this.device1,
    required this.device2,
    required this.device3,
    required this.exportPath,
  });

  factory AllDevicesTables.fromMap(Map<dynamic, dynamic> map) {
    final countsRaw = map['counts'];
    final counts = <String, int>{};
    if (countsRaw is Map) {
      countsRaw.forEach((key, value) {
        counts[key.toString()] = _asInt(value);
      });
    }

    return AllDevicesTables(
      counts: counts,
      device1: _parseRows(map['device1']),
      device2: _parseRows(map['device2']),
      device3: _parseRows(map['device3']),
      exportPath: map['export_path']?.toString() ?? '',
    );
  }

  static List<RuntimeMetric> _parseRows(dynamic raw) {
    if (raw is! List) return [];
    return raw.whereType<Map>().map((e) => RuntimeMetric.fromMap(e)).toList();
  }
}

int _asInt(dynamic value) {
  if (value is int) return value;
  if (value is double) return value.round();
  return int.tryParse(value?.toString() ?? '') ?? 0;
}
