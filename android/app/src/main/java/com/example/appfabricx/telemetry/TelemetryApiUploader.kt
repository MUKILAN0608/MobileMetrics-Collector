package com.example.appfabricx.telemetry

import android.content.Context
import android.util.Log
import com.example.appfabricx.data.AppDatabase
import com.example.appfabricx.data.RuntimeMetricDao
import com.example.appfabricx.data.RuntimeMetricRecord
import com.example.appfabricx.data.RuntimeMetricSchema
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelemetryApiUploader(context: Context) {

    private val appContext = context.applicationContext
    private val config = TelemetryApiConfig(appContext)
    private val dao: RuntimeMetricDao = AppDatabase.getInstance(appContext).runtimeMetricDao()
    private val deviceAssignment = DeviceAssignmentManager(appContext)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Upload newly persisted row(s) and any backlog not yet synced.
     */
    fun syncAfterPersist(
        @Suppress("UNUSED_PARAMETER") persistResult: PersistResult,
        @Suppress("UNUSED_PARAMETER") record: RuntimeMetricRecord,
    ): ApiSyncResult = syncPending()

    fun syncPending(): ApiSyncResult {
        if (!config.isEnabled()) {
            return ApiSyncResult.skipped("API URL not configured")
        }

        val baseUrl = config.getBaseUrl()
        val deviceId = config.getDeviceId()
        val userLabel = deviceAssignment.getUserLabel()
        val batches = mutableMapOf<Int, MutableList<JSONObject>>()

        // Upload all local tables (old + new data) into one laptop table via API.
        for (slot in 1..3) {
            val afterId = config.getLastSyncedRecordId(slot)
            val backlog = loadBacklog(slot, afterId, BATCH_LIMIT)
            for (entity in backlog) {
                val record = entityToRecord(slot, entity)
                val recordId = entityRecordId(slot, entity)
                batches.getOrPut(slot) { mutableListOf() }
                    .add(record.toApiJson(recordId, slot))
            }
        }

        if (batches.isEmpty()) {
            return ApiSyncResult.ok(0, 0)
        }

        var totalInserted = 0
        var totalIgnored = 0
        var lastError: String? = null

        for ((slot, records) in batches) {
            if (records.isEmpty()) continue
            val result = postBatch(
                baseUrl = baseUrl,
                deviceId = deviceId,
                deviceSlot = slot,
                userLabel = userLabel,
                records = records,
            )
            if (result.ok) {
                totalInserted += result.inserted
                totalIgnored += result.ignored
                val maxRecordId = records.maxOf { it.getLong("record_id") }
                if (maxRecordId > config.getLastSyncedRecordId(slot)) {
                    config.setLastSyncedRecordId(slot, maxRecordId)
                }
            } else {
                lastError = result.error
                break
            }
        }

        config.setLastSyncAtMs(System.currentTimeMillis())
        config.setLastSyncInserted(totalInserted)
        config.setLastSyncError(lastError)

        return if (lastError != null) {
            ApiSyncResult.failure(lastError)
        } else {
            ApiSyncResult.ok(totalInserted, totalIgnored)
        }
    }

    fun testConnection(): ApiSyncResult {
        if (!config.isEnabled()) {
            return ApiSyncResult.failure("Set API base URL first")
        }
        val url = "${config.getBaseUrl()}/health"
        return try {
            val request = buildRequest(url, null)
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ApiSyncResult.ok(0, 0, message = "Server reachable")
                } else {
                    ApiSyncResult.failure("HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            ApiSyncResult.failure(e.message ?: "Connection failed")
        }
    }

    private fun postBatch(
        baseUrl: String,
        deviceId: String,
        deviceSlot: Int,
        userLabel: String,
        records: List<JSONObject>,
    ): PostBatchResult {
        val url = "$baseUrl/api/v1/telemetry"
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("device_slot", deviceSlot)
            put("user_label", userLabel)
            put("records", JSONArray(records))
        }

        return try {
            val request = buildRequest(url, body.toString())
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return PostBatchResult(false, 0, 0, "HTTP ${response.code}: $responseBody")
                }
                val json = JSONObject(responseBody)
                PostBatchResult(
                    ok = json.optBoolean("ok", true),
                    inserted = json.optInt("inserted", 0),
                    ignored = json.optInt("ignored", 0),
                    error = if (json.optBoolean("ok", true)) null else json.optString("error"),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "API upload failed", e)
            PostBatchResult(false, 0, 0, e.message ?: "Upload failed")
        }
    }

    private fun buildRequest(url: String, jsonBody: String?): Request {
        val builder = Request.Builder().url(url)
        val apiKey = config.getApiKey()
        if (apiKey.isNotEmpty()) {
            builder.header("X-Api-Key", apiKey)
        }
        // ngrok free tier: skip interstitial for API clients
        if (config.getBaseUrl().contains("ngrok", ignoreCase = true)) {
            builder.header("ngrok-skip-browser-warning", "true")
        }
        if (jsonBody != null) {
            builder.post(jsonBody.toRequestBody(jsonMediaType))
        } else {
            builder.get()
        }
        return builder.build()
    }

    private fun loadBacklog(deviceSlot: Int, afterId: Long, limit: Int): List<Any> {
        return when (deviceSlot) {
            1 -> dao.device1AfterRecordId(afterId, limit)
            2 -> dao.device2AfterRecordId(afterId, limit)
            else -> dao.device3AfterRecordId(afterId, limit)
        }
    }

    private fun entityToRecord(slot: Int, entity: Any): RuntimeMetricRecord {
        return when (slot) {
            1 -> RuntimeMetricRecord.fromDevice1(entity as com.example.appfabricx.data.Device1RuntimeMetric)
            2 -> RuntimeMetricRecord.fromDevice2(entity as com.example.appfabricx.data.Device2RuntimeMetric)
            else -> RuntimeMetricRecord.fromDevice3(entity as com.example.appfabricx.data.Device3RuntimeMetric)
        }
    }

    private fun entityRecordId(slot: Int, entity: Any): Long {
        return when (slot) {
            1 -> (entity as com.example.appfabricx.data.Device1RuntimeMetric).recordId
            2 -> (entity as com.example.appfabricx.data.Device2RuntimeMetric).recordId
            3 -> (entity as com.example.appfabricx.data.Device3RuntimeMetric).recordId
            else -> 0L
        }
    }

    private fun RuntimeMetricRecord.toApiJson(recordId: Long, deviceSlot: Int): JSONObject {
        return JSONObject().apply {
            put("record_id", recordId)
            put("table_name", RuntimeMetricSchema.UNIFIED_SERVER_TABLE)
            put("device_slot", deviceSlot)
            put("timestamp", timestamp)
            put("cpu_usage", cpuUsage.toDouble())
            put("memory_used", memoryUsed)
            put("memory_available", memoryAvailable)
            put("battery_percentage", batteryPercentage)
            put("charging_status", chargingStatus)
            put("device_temperature", deviceTemperature.toDouble())
            put("process_count", processCount)
            put("foreground_app", foregroundApp)
            put("running_apps_count", runningAppsCount)
            put("runtime_state", runtimeState)
        }
    }

    private data class PostBatchResult(
        val ok: Boolean,
        val inserted: Int,
        val ignored: Int,
        val error: String?,
    )

    data class ApiSyncResult(
        val success: Boolean,
        val skipped: Boolean,
        val inserted: Int,
        val ignored: Int,
        val error: String?,
        val message: String?,
    ) {
        companion object {
            fun ok(inserted: Int, ignored: Int, message: String? = null) =
                ApiSyncResult(true, false, inserted, ignored, null, message)

            fun skipped(reason: String) =
                ApiSyncResult(true, true, 0, 0, null, reason)

            fun failure(error: String) =
                ApiSyncResult(false, false, 0, 0, error, null)
        }

        fun toMap(): Map<String, Any?> = mapOf(
            "success" to success,
            "skipped" to skipped,
            "inserted" to inserted,
            "ignored" to ignored,
            "error" to error,
            "message" to message,
        )
    }

    companion object {
        private const val TAG = "TelemetryApiUploader"
        private const val BATCH_LIMIT = 50
    }
}
