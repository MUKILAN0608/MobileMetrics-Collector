package com.example.appfabricx.telemetry

import android.content.Context
import android.util.Log
import com.example.appfabricx.data.AppDatabase
import com.example.appfabricx.data.RuntimeMetricDao
import com.example.appfabricx.data.RuntimeMetricRecord
import com.example.appfabricx.data.RuntimeMetricSchema
import com.example.appfabricx.data.RuntimeStateCount
import java.util.concurrent.Executors

class RuntimeTelemetryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao: RuntimeMetricDao = AppDatabase.getInstance(appContext).runtimeMetricDao()
    private val deviceAssignment = DeviceAssignmentManager(appContext)
    private val csvExporter = TelemetryCsvExporter(appContext)
    private val apiUploader = TelemetryApiUploader(appContext)
    private val apiConfig = TelemetryApiConfig(appContext)
    private val urlDiscovery = TelemetryUrlDiscovery(apiConfig, apiUploader)
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private val apiExecutor = Executors.newSingleThreadExecutor()
    private var recordsSinceLastExport = 0
    @Volatile
    private var lastApiSync: Map<String, Any?>? = null

    /**
     * Persists all 12 schema fields to exactly one user table (isolated per device assignment).
     */
    fun persist(snapshot: RuntimeTelemetryCollector.Snapshot): PersistResult {
        return try {
            val record = RuntimeMetricRecord.fromSnapshot(snapshot)
            if (!record.isValid()) {
                return PersistResult(
                    success = false,
                    lastRecordId = -1,
                    writtenTable = "",
                    writtenTables = emptyList(),
                    totalDevice1 = 0,
                    totalDevice2 = 0,
                    totalDevice3 = 0,
                    errorMessage = "Invalid telemetry record — missing required fields",
                )
            }

            val deviceSlot = deviceAssignment.getAssignedDevice()
            val lastId: Long
            val writtenTables: List<String>

            if (deviceAssignment.isMirrorToAllTables()) {
                lastId = dao.insertAllDevices(
                    record.toDevice1Entity(),
                    record.toDevice2Entity(),
                    record.toDevice3Entity(),
                )
                writtenTables = listOf(
                    RuntimeMetricSchema.TABLE_DEVICE_1,
                    RuntimeMetricSchema.TABLE_DEVICE_2,
                    RuntimeMetricSchema.TABLE_DEVICE_3,
                )
            } else {
                lastId = insertToIsolatedUserTable(deviceSlot, record)
                writtenTables = listOf(RuntimeMetricSchema.tableForDeviceSlot(deviceSlot))
            }

            val counts = getRecordCounts()
            scheduleExportIfNeeded()

            scheduleApiSync()

            PersistResult(
                success = true,
                lastRecordId = lastId,
                writtenTable = writtenTables.first(),
                writtenTables = writtenTables,
                totalDevice1 = counts["device1"] ?: 0,
                totalDevice2 = counts["device2"] ?: 0,
                totalDevice3 = counts["device3"] ?: 0,
                assignedDeviceSlot = deviceSlot,
                userLabel = deviceAssignment.getUserLabel(),
                apiSync = lastApiSync,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist runtime telemetry", e)
            PersistResult(
                success = false,
                lastRecordId = -1,
                writtenTable = "",
                writtenTables = emptyList(),
                totalDevice1 = 0,
                totalDevice2 = 0,
                totalDevice3 = 0,
                errorMessage = e.message,
            )
        }
    }

    private fun insertToIsolatedUserTable(deviceSlot: Int, record: RuntimeMetricRecord): Long {
        return when (deviceSlot.coerceIn(1, 3)) {
            1 -> dao.insertDevice1(record.toDevice1Entity())
            2 -> dao.insertDevice2(record.toDevice2Entity())
            else -> dao.insertDevice3(record.toDevice3Entity())
        }
    }

    private fun scheduleExportIfNeeded() {
        recordsSinceLastExport++
        if (recordsSinceLastExport < EXPORT_EVERY_N_RECORDS) return
        recordsSinceLastExport = 0
        exportExecutor.execute {
            try {
                csvExporter.exportAllTables()
            } catch (e: Exception) {
                Log.w(TAG, "CSV export failed", e)
            }
        }
    }

    fun getAssignedDevice(): Int = deviceAssignment.getAssignedDevice()

    fun setAssignedDevice(deviceSlot: Int) {
        deviceAssignment.setAssignedDevice(deviceSlot)
    }

    fun getUserLabel(): String = deviceAssignment.getUserLabel()

    fun setUserLabel(label: String) {
        deviceAssignment.setUserLabel(label)
    }

    fun isMirrorToAllTables(): Boolean = deviceAssignment.isMirrorToAllTables()

    fun setMirrorToAllTables(enabled: Boolean) {
        deviceAssignment.setMirrorToAllTables(enabled)
    }

    fun isIsolatedPerUserTables(): Boolean = deviceAssignment.isIsolatedPerUserTables()

    fun getExportDirectoryPath(): String = csvExporter.getExportDirectoryPath()

    fun getApiSyncStatus(): Map<String, Any?> {
        val status = apiConfig.getStatusMap().toMutableMap()
        lastApiSync?.let { status["last_upload"] = it }
        return status
    }

    fun ensureApiConfigured(): Map<String, Any?> {
        apiConfig.ensureDefaultUrlIfEmpty()
        val sync = urlDiscovery.discoverAndApply()
        lastApiSync = sync.toMap()
        if (sync.success && !sync.skipped) {
            com.example.appfabricx.MainActivity.notifyApiSyncUpdate(sync.toMap())
        }
        val test = mapOf(
            "success" to sync.success,
            "skipped" to sync.skipped,
            "error" to sync.error,
            "message" to sync.message,
        )
        return mapOf(
            "configured" to apiConfig.isEnabled(),
            "test" to test,
            "sync" to sync.toMap(),
            "status" to getApiSyncStatus(),
            "auto_discovered" to true,
        )
    }

    fun rediscoverApiIfNeeded(): Boolean {
        if (apiConfig.getLastSyncError().isBlank() && apiConfig.getLastSyncAtMs() > 0) {
            return true
        }
        val sync = urlDiscovery.discoverAndApply()
        lastApiSync = sync.toMap()
        com.example.appfabricx.MainActivity.notifyApiSyncUpdate(sync.toMap())
        return sync.success
    }

    fun setApiBaseUrl(url: String) {
        apiConfig.setBaseUrl(url)
    }

    fun setApiKey(key: String) = apiConfig.setApiKey(key)

    fun configureApi(baseUrl: String, apiKey: String = ""): Map<String, Any?> {
        val url = baseUrl.ifBlank { TelemetryApiDefaults.DEFAULT_BASE_URL }
        apiConfig.enforceConstantBaseUrl(url)
        apiConfig.setApiKey(apiKey)
        val test = apiUploader.testConnection()
        val sync = if (test.success && !test.skipped) {
            apiUploader.syncPending()
        } else {
            null
        }
        if (sync != null) {
            lastApiSync = sync.toMap()
            com.example.appfabricx.MainActivity.notifyApiSyncUpdate(lastApiSync!!)
        }
        return mapOf(
            "configured" to apiConfig.isEnabled(),
            "test" to test.toMap(),
            "sync" to sync?.toMap(),
            "status" to getApiSyncStatus(),
        )
    }

    fun testApiConnection(): Map<String, Any?> =
        apiUploader.testConnection().toMap()

    fun syncPendingToApi(): Map<String, Any?> {
        val result = apiUploader.syncPending().toMap()
        lastApiSync = result
        com.example.appfabricx.MainActivity.notifyApiSyncUpdate(result)
        return result
    }

    private fun scheduleApiSync() {
        apiExecutor.execute {
            try {
                if (!apiConfig.isEnabled() || apiConfig.getLastSyncError().isNotBlank()) {
                    urlDiscovery.discoverAndApply()
                }
                if (!apiConfig.isEnabled()) return@execute
                val result = apiUploader.syncPending().toMap()
                lastApiSync = result
                com.example.appfabricx.MainActivity.notifyApiSyncUpdate(result)
                if (result["success"] != true) {
                    apiConfig.setLastSyncError(result["error"]?.toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background API sync failed", e)
                val err = mapOf(
                    "success" to false,
                    "skipped" to false,
                    "inserted" to 0,
                    "ignored" to 0,
                    "error" to (e.message ?: "sync failed"),
                    "message" to null,
                )
                lastApiSync = err
                apiConfig.setLastSyncError(e.message)
                com.example.appfabricx.MainActivity.notifyApiSyncUpdate(err)
            }
        }
    }

    fun verifyDatabase(): Map<String, Any?> {
        return try {
            val counts = getRecordCounts()
            mapOf(
                "ok" to true,
                "counts" to counts,
                "columns" to RuntimeMetricSchema.COLUMNS,
                "isolated_mode" to isIsolatedPerUserTables(),
                "assigned_device" to getAssignedDevice(),
                "assigned_table" to RuntimeMetricSchema.tableForDeviceSlot(getAssignedDevice()),
                "db_path" to appContext.getDatabasePath("appfabric_runtime_telemetry.db").absolutePath,
            )
        } catch (e: Exception) {
            mapOf(
                "ok" to false,
                "error" to e.message,
            )
        }
    }

    fun getAllDevicesTables(limitPerDevice: Int = 8): Map<String, Any?> {
        return mapOf(
            "counts" to getRecordCounts(),
            "device1" to dao.recentDevice1(limitPerDevice).map {
                RuntimeMetricRecord.fromDevice1(it).toMap(it.recordId)
            },
            "device2" to dao.recentDevice2(limitPerDevice).map {
                RuntimeMetricRecord.fromDevice2(it).toMap(it.recordId)
            },
            "device3" to dao.recentDevice3(limitPerDevice).map {
                RuntimeMetricRecord.fromDevice3(it).toMap(it.recordId)
            },
            "export_path" to csvExporter.getExportDirectoryPath(),
            "db_path" to appContext.getDatabasePath("appfabric_runtime_telemetry.db").absolutePath,
            "columns" to RuntimeMetricSchema.COLUMNS,
            "assigned_device" to getAssignedDevice(),
            "isolated_mode" to isIsolatedPerUserTables(),
        )
    }

    fun getRecordCounts(): Map<String, Int> {
        return mapOf(
            "device1" to dao.countDevice1(),
            "device2" to dao.countDevice2(),
            "device3" to dao.countDevice3(),
        )
    }

    fun getStateDistribution(deviceSlot: Int): List<RuntimeStateCount> {
        return when (deviceSlot.coerceIn(1, 3)) {
            1 -> dao.stateDistributionDevice1()
            2 -> dao.stateDistributionDevice2()
            else -> dao.stateDistributionDevice3()
        }
    }

    fun getRecentMetrics(deviceSlot: Int, limit: Int): List<Map<String, Any?>> {
        return when (deviceSlot.coerceIn(1, 3)) {
            1 -> dao.recentDevice1(limit).map {
                RuntimeMetricRecord.fromDevice1(it).toMap(it.recordId)
            }
            2 -> dao.recentDevice2(limit).map {
                RuntimeMetricRecord.fromDevice2(it).toMap(it.recordId)
            }
            else -> dao.recentDevice3(limit).map {
                RuntimeMetricRecord.fromDevice3(it).toMap(it.recordId)
            }
        }
    }

    companion object {
        private const val TAG = "RuntimeTelemetryRepo"
        private const val EXPORT_EVERY_N_RECORDS = 15
    }
}
