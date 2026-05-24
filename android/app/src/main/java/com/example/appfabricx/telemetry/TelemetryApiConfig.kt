package com.example.appfabricx.telemetry

import android.content.Context
import java.util.UUID

/**
 * API sync settings (mobile data / Wi‑Fi — no USB).
 * Base URL example: http://192.168.1.10:3847
 */
class TelemetryApiConfig(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = getBaseUrl().isNotBlank()

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, "")?.trim().orEmpty().trimEnd('/')

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(url)).apply()
    }

    fun ensureDefaultUrlIfEmpty(): Boolean {
        enforceConstantBaseUrl(TelemetryApiDefaults.DEFAULT_BASE_URL)
        return getBaseUrl() == TelemetryApiDefaults.DEFAULT_BASE_URL
    }

    /** Always use the URL baked into the APK (ignores old saved ngrok URLs). */
    fun enforceConstantBaseUrl(constantUrl: String) {
        val normalized = normalizeBaseUrl(constantUrl)
        if (getBaseUrl() != normalized) {
            setBaseUrl(normalized)
        }
    }

    fun getRecentWorkingUrls(): List<String> {
        val raw = prefs.getString(KEY_RECENT_URLS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun addRecentWorkingUrl(url: String) {
        val normalized = normalizeBaseUrl(url)
        if (normalized.isBlank()) return
        val updated = (listOf(normalized) + getRecentWorkingUrls())
            .distinct()
            .take(8)
        prefs.edit().putString(KEY_RECENT_URLS, updated.joinToString("\n")).apply()
    }

    fun applyDiscoveredUrl(url: String) {
        setBaseUrl(url)
        addRecentWorkingUrl(url)
    }

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "")?.trim().orEmpty()

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = "appfabric-${UUID.randomUUID()}"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getLastSyncedRecordId(deviceSlot: Int): Long {
        return prefs.getLong(keyForSlot(deviceSlot), 0L)
    }

    fun setLastSyncedRecordId(deviceSlot: Int, recordId: Long) {
        prefs.edit().putLong(keyForSlot(deviceSlot), recordId).apply()
    }

    fun getLastSyncAtMs(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun setLastSyncAtMs(ms: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, ms).apply()
    }

    fun getLastSyncError(): String = prefs.getString(KEY_LAST_SYNC_ERROR, "") ?: ""

    fun setLastSyncError(message: String?) {
        prefs.edit().putString(KEY_LAST_SYNC_ERROR, message ?: "").apply()
    }

    fun getLastSyncInserted(): Int = prefs.getInt(KEY_LAST_INSERTED, 0)

    fun setLastSyncInserted(count: Int) {
        prefs.edit().putInt(KEY_LAST_INSERTED, count).apply()
    }

    fun getStatusMap(): Map<String, Any?> {
        return mapOf(
            "enabled" to isEnabled(),
            "base_url" to getBaseUrl(),
            "device_id" to getDeviceId(),
            "has_api_key" to getApiKey().isNotEmpty(),
            "last_sync_at_ms" to getLastSyncAtMs(),
            "last_sync_error" to getLastSyncError(),
            "last_inserted" to getLastSyncInserted(),
            "last_synced_record_id_device1" to getLastSyncedRecordId(1),
            "last_synced_record_id_device2" to getLastSyncedRecordId(2),
            "last_synced_record_id_device3" to getLastSyncedRecordId(3),
        )
    }

    private fun keyForSlot(deviceSlot: Int): String = "last_synced_record_id_device_$deviceSlot"

    companion object {
        private const val PREFS_NAME = "telemetry_api_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC_AT = "last_sync_at_ms"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_LAST_INSERTED = "last_inserted"
        private const val KEY_RECENT_URLS = "recent_working_urls"

        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return ""
            url = url.removeSuffix("/")
            url = url.removeSuffix("/health")
            url = url.removeSuffix("/api/v1/telemetry")
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return url.trimEnd('/')
        }
    }
}
