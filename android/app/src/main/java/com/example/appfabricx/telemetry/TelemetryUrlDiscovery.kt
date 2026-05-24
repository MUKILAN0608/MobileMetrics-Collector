package com.example.appfabricx.telemetry

import android.util.Log

/**
 * Uses the single constant URL from TelemetryApiDefaults (config/api-endpoint.txt).
 * Does not rotate through stale ngrok URLs.
 */
class TelemetryUrlDiscovery(
    private val config: TelemetryApiConfig,
    private val uploader: TelemetryApiUploader,
) {

    fun discoverAndApply(): TelemetryApiUploader.ApiSyncResult {
        val constantUrl = TelemetryApiDefaults.DEFAULT_BASE_URL
        config.enforceConstantBaseUrl(constantUrl)
        Log.i(TAG, "Using constant API URL: $constantUrl")

        val test = uploader.testConnection()
        if (!test.success || test.skipped) {
            val msg = test.error
                ?: "Cannot reach $constantUrl — run ngrok http 3847 on laptop"
            config.setLastSyncError(msg)
            return TelemetryApiUploader.ApiSyncResult.failure(msg)
        }

        config.setLastSyncError(null)
        config.addRecentWorkingUrl(constantUrl)
        return uploader.syncPending()
    }

    companion object {
        private const val TAG = "TelemetryUrlDiscovery"
    }
}
