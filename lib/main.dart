import 'package:flutter/material.dart';

import 'screens/dashboard_screen.dart';

void main() {
  runApp(const AppFabricApp());
}

class AppFabricApp extends StatelessWidget {
  const AppFabricApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AppFabric X∞',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigo),
        useMaterial3: true,
      ),
      home: const DashboardScreen(),
    );
  }
}
