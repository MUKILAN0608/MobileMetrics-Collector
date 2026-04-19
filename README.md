# AppFabric X

Flutter app that streams Android system data (memory and battery) from a native foreground service to Flutter UI through a MethodChannel.

## What It Does (Current Status)

AppFabric X starts an Android foreground service when the app opens, reads device memory usage and battery percentage every 2 seconds, and sends that data to Flutter through a native channel. The Flutter screen displays the latest value in near real time.

Current output format on screen:

- Memory: <used_memory_in_bytes> | Battery: <battery_percent>

In short, this is a working Android-to-Flutter system monitoring pipeline with a minimal live dashboard UI.

## Features

- Native Android foreground service (`SystemMonitorService`) runs periodic system monitoring
- Data sent from Android to Flutter using `MethodChannel` (`appfabric/channel`)
- Flutter UI updates live values in the app screen
- Android 14+ compatible foreground service configuration

## Tech Stack

- Flutter (Dart)
- Android native layer in Kotlin
- MethodChannel bridge between Kotlin and Dart

## Project Structure

- `lib/main.dart`: Flutter UI and channel handler (`updateData`)
- `android/app/src/main/kotlin/com/example/appfabricx/MainActivity.kt`: Flutter activity and channel initialization
- `android/app/src/main/java/com/example/appfabricx/SystemMonitorService.kt`: Foreground service and system data collection
- `android/app/src/main/AndroidManifest.xml`: Service declaration, permissions, and Flutter embedding metadata

## Requirements

- Flutter SDK (stable)
- Dart SDK (bundled with Flutter)
- Android Studio + Android SDK
- Connected Android device or running emulator

## Getting Started

1. Install dependencies:

```bash
flutter pub get
```

2. Verify environment:

```bash
flutter doctor
```

3. Run the app:

```bash
flutter run
```

## How It Works

1. `MainActivity` starts `SystemMonitorService` in `onStart()`.
2. `SystemMonitorService` starts as a foreground service with a notification.
3. Service collects memory and battery information every 2 seconds.
4. Service sends payload string to Flutter via `MainActivity.channel?.invokeMethod("updateData", data)`.
5. Flutter receives `updateData` and updates the displayed text.

## Android Notes

This project includes Android foreground service requirements for newer Android versions:

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- `android:foregroundServiceType="dataSync"` on the service
- `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on supported API levels
- Flutter v2 embedding metadata in manifest (`flutterEmbedding = 2`)

## Troubleshooting

### Build failed due to use of deleted Android v1 embedding

Ensure `AndroidManifest.xml` includes:

```xml
<meta-data
	android:name="flutterEmbedding"
	android:value="2" />
```

### MissingForegroundServiceTypeException / Lost connection to device

Ensure all of the following are set:

- Foreground service type in manifest
- `FOREGROUND_SERVICE_DATA_SYNC` permission
- Correct `startForeground(...)` overload with service type on Android Q+

### Android SDK not detected (Windows)

Set the SDK path, for example:

```bash
flutter config --android-sdk C:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
```

## Development Tips

- Use `flutter logs` for runtime diagnosis.
- Use `flutter clean` if Gradle/Flutter caches get out of sync.
- Keep Android SDK tools outside the project source tree (do not copy SDK folders into `android/app/src/main/...`).

## License

This repository is licensed under the terms in `LICENSE`.
