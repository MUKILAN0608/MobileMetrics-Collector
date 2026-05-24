import 'package:appfabricx/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('App loads dashboard', (WidgetTester tester) async {
    await tester.pumpWidget(const AppFabricApp());
    expect(find.text('AppFabric X∞ Runtime Telemetry'), findsOneWidget);
  });
}
