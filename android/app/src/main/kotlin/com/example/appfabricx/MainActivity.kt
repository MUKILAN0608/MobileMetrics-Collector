package com.example.appfabricx

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import com.example.appfabricx.telemetry.RuntimeTelemetryRepository
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {

    companion object {
        private const val CHANNEL_NAME = "appfabric/channel"

        @Volatile
        private var channel: MethodChannel? = null

        @Volatile
        var isFlutterEngineAttached: Boolean = false
            private set

        fun notifyTelemetryUpdate(payload: Map<String, Any?>) {
            invokeOnFlutter("updateTelemetry", payload)
        }

        fun notifyApiSyncUpdate(payload: Map<String, Any?>) {
            invokeOnFlutter("apiSyncUpdate", payload)
        }

        private fun invokeOnFlutter(method: String, payload: Map<String, Any?>) {
            if (!isFlutterEngineAttached) return
            val activeChannel = channel ?: return
            Handler(Looper.getMainLooper()).post {
                if (!isFlutterEngineAttached) return@post
                try {
                    activeChannel.invokeMethod(method, payload)
                } catch (_: Exception) {
                    // Flutter engine detached — native DB/API still runs.
                }
            }
        }
    }

    private var usageAccessPrompted = false
    private lateinit var telemetryRepository: RuntimeTelemetryRepository
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        telemetryRepository = RuntimeTelemetryRepository(this)
        dbExecutor.execute {
            telemetryRepository.ensureApiConfigured()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        isFlutterEngineAttached = true
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
        channel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "setDeviceAssignment" -> {
                    val slot = call.argument<Int>("device_slot") ?: 1
                    telemetryRepository.setAssignedDevice(slot)
                    result.success(slot.coerceIn(1, 3))
                }
                "getDeviceAssignment" -> {
                    result.success(telemetryRepository.getAssignedDevice())
                }
                "getRecordCounts" -> runOnDbThread(result) {
                    telemetryRepository.getRecordCounts()
                }
                "getRecentMetrics" -> {
                    val deviceSlot = call.argument<Int>("device_slot")
                        ?: telemetryRepository.getAssignedDevice()
                    val limit = call.argument<Int>("limit") ?: 20
                    runOnDbThread(result) {
                        telemetryRepository.getRecentMetrics(deviceSlot, limit)
                    }
                }
                "getStateDistribution" -> {
                    val deviceSlot = call.argument<Int>("device_slot")
                        ?: telemetryRepository.getAssignedDevice()
                    runOnDbThread(result) {
                        telemetryRepository.getStateDistribution(deviceSlot).map {
                            mapOf(
                                "runtime_state" to it.runtimeState,
                                "count" to it.count,
                            )
                        }
                    }
                }
                "startMonitoring" -> {
                    startMonitorService()
                    result.success(true)
                }
                "stopMonitoring" -> {
                    stopService(Intent(this, SystemMonitorService::class.java))
                    result.success(true)
                }
                "hasUsageAccess" -> {
                    result.success(hasUsageAccess(this))
                }
                "openUsageAccessSettings" -> {
                    requestUsageAccess(this)
                    result.success(true)
                }
                "getAllDevicesTables" -> {
                    val limit = call.argument<Int>("limit") ?: 8
                    runOnDbThread(result) {
                        telemetryRepository.getAllDevicesTables(limit)
                    }
                }
                "setMirrorToAllTables" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    telemetryRepository.setMirrorToAllTables(enabled)
                    result.success(enabled)
                }
                "isMirrorToAllTables" -> {
                    result.success(telemetryRepository.isMirrorToAllTables())
                }
                "getExportPath" -> {
                    result.success(telemetryRepository.getExportDirectoryPath())
                }
                "verifyDatabase" -> runOnDbThread(result) {
                    telemetryRepository.verifyDatabase()
                }
                "getApiSyncStatus" -> {
                    result.success(telemetryRepository.getApiSyncStatus())
                }
                "ensureApiConfigured" -> runOnDbThread(result) {
                    telemetryRepository.ensureApiConfigured()
                }
                "configureApi" -> {
                    val url = call.argument<String>("base_url") ?: ""
                    val key = call.argument<String>("api_key") ?: ""
                    runOnDbThread(result) {
                        telemetryRepository.configureApi(url, key)
                    }
                }
                "setApiBaseUrl" -> {
                    val url = call.argument<String>("base_url") ?: ""
                    telemetryRepository.setApiBaseUrl(url)
                    result.success(url)
                }
                "setApiKey" -> {
                    val key = call.argument<String>("api_key") ?: ""
                    telemetryRepository.setApiKey(key)
                    result.success(true)
                }
                "testApiConnection" -> runOnDbThread(result) {
                    telemetryRepository.testApiConnection()
                }
                "syncPendingToApi" -> runOnDbThread(result) {
                    telemetryRepository.syncPendingToApi()
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        isFlutterEngineAttached = false
        channel?.setMethodCallHandler(null)
        channel = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    private fun runOnDbThread(result: MethodChannel.Result, block: () -> Any?) {
        dbExecutor.execute {
            try {
                val value = block()
                mainHandler.post { result.success(value) }
            } catch (e: Exception) {
                mainHandler.post {
                    result.error("db_error", e.message, null)
                }
            }
        }
    }

    override fun onDestroy() {
        isFlutterEngineAttached = false
        channel = null
        dbExecutor.shutdown()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        startMonitorService()
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageAccess(this) && !usageAccessPrompted) {
            usageAccessPrompted = true
            requestUsageAccess(this)
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, SystemMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestUsageAccess(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
