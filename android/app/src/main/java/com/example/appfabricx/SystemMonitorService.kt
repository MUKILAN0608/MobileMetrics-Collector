package com.example.appfabricx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.appfabricx.telemetry.ForegroundAppTracker
import com.example.appfabricx.telemetry.PersistResult
import com.example.appfabricx.telemetry.RuntimeStateClassifier
import com.example.appfabricx.telemetry.RuntimeTelemetryCollector
import com.example.appfabricx.telemetry.RuntimeTelemetryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that continuously collects Android runtime telemetry
 * every 2–5 seconds and persists structured records to Room.
 */
class SystemMonitorService : Service() {

    private lateinit var monitorThread: HandlerThread
    private lateinit var monitorHandler: Handler

    private lateinit var collector: RuntimeTelemetryCollector
    private lateinit var stateClassifier: RuntimeStateClassifier
    private lateinit var foregroundTracker: ForegroundAppTracker
    private lateinit var repository: RuntimeTelemetryRepository

    private var recordsCollected = 0L

    private val monitorTask = object : Runnable {
        override fun run() {
            collectAndPersist()
            val delayMs = collector.nextCollectionDelayMs()
            monitorHandler.postDelayed(this, delayMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        collector = RuntimeTelemetryCollector(this)
        stateClassifier = RuntimeStateClassifier(this)
        foregroundTracker = ForegroundAppTracker()
        repository = RuntimeTelemetryRepository(this)

        monitorThread = HandlerThread("SystemMonitorWorker")
        monitorThread.start()
        monitorHandler = Handler(monitorThread.looper)

        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val channelId = "monitor_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AppFabric Runtime Telemetry",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Continuous runtime monitoring for AppFabric X"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val deviceSlot = repository.getAssignedDevice()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AppFabric X — Device $deviceSlot Monitoring")
            .setContentText("Collecting runtime telemetry every 2–5s")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoring() {
        monitorHandler.post(monitorTask)
    }

    private fun collectAndPersist() {
        val snapshot = collector.collect(stateClassifier, foregroundTracker)
        val persistResult = repository.persist(snapshot)
        recordsCollected++

        val payload = buildTelemetryPayload(snapshot, persistResult)
        sendToFlutter(payload)
        updateNotification(snapshot, persistResult)
    }

    private fun buildTelemetryPayload(
        snapshot: RuntimeTelemetryCollector.Snapshot,
        persistResult: PersistResult,
    ): Map<String, Any?> {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val memoryUsedMb = snapshot.memoryUsedBytes / (1024.0 * 1024.0)
        val memoryAvailableMb = snapshot.memoryAvailableBytes / (1024.0 * 1024.0)

        return mapOf(
            "timestamp" to formatter.format(Date(snapshot.timestampMs)),
            "timestamp_ms" to snapshot.timestampMs,
            "cpu_usage" to snapshot.cpuUsagePercent,
            "memory_used" to snapshot.memoryUsedBytes,
            "memory_available" to snapshot.memoryAvailableBytes,
            "memory_used_mb" to memoryUsedMb,
            "memory_available_mb" to memoryAvailableMb,
            "memory_used_percent" to snapshot.memoryUsedPercent,
            "battery_percentage" to snapshot.batteryPercentage,
            "charging_status" to snapshot.chargingStatus,
            "device_temperature" to snapshot.deviceTemperatureC,
            "process_count" to snapshot.processCount,
            "foreground_app" to snapshot.foregroundApp,
            "running_apps_count" to snapshot.runningAppsCount,
            "runtime_state" to snapshot.runtimeState,
            "app_switches_in_interval" to snapshot.appSwitchesInInterval,
            "assigned_device" to repository.getAssignedDevice(),
            "records_collected_session" to recordsCollected,
            "table_name" to persistResult.writtenTable,
            "db_saved" to persistResult.success,
            "db_last_record_id" to persistResult.lastRecordId,
            "db_total_device1" to persistResult.totalDevice1,
            "db_total_device2" to persistResult.totalDevice2,
            "db_total_device3" to persistResult.totalDevice3,
            "db_written_tables" to persistResult.writtenTables,
            "db_error" to persistResult.errorMessage,
            "assigned_device_slot" to persistResult.assignedDeviceSlot,
            "user_label" to persistResult.userLabel,
            "isolated_mode" to repository.isIsolatedPerUserTables(),
            "api_sync" to persistResult.apiSync,
        )
    }

    private fun updateNotification(
        snapshot: RuntimeTelemetryCollector.Snapshot,
        persistResult: PersistResult,
    ) {
        val channelId = "monitor_channel"
        val dbLabel = if (persistResult.success) "DB OK" else "DB ERR"
        val apiLabel = apiSyncLabel(persistResult.apiSync)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Device ${repository.getAssignedDevice()} — ${snapshot.runtimeState}")
            .setContentText(
                "$dbLabel | $apiLabel | CPU ${String.format(Locale.US, "%.0f", snapshot.cpuUsagePercent)}% | " +
                    "D1:${persistResult.totalDevice1} D2:${persistResult.totalDevice2} " +
                    "D3:${persistResult.totalDevice3}",
            )
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendToFlutter(payload: Map<String, Any?>) {
        MainActivity.notifyTelemetryUpdate(payload)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        monitorHandler.removeCallbacks(monitorTask)
        monitorThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun apiSyncLabel(apiSync: Map<String, Any?>?): String {
        if (apiSync == null) return "API …"
        val skipped = apiSync["skipped"] == true
        if (skipped) return "API off"
        val success = apiSync["success"] == true
        val error = apiSync["error"]?.toString().orEmpty()
        if (!success && error.isNotEmpty()) return "API ERR"
        val inserted = (apiSync["inserted"] as? Number)?.toInt() ?: 0
        return if (inserted > 0) "API +$inserted" else "API OK"
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
