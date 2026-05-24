package com.example.appfabricx.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

/**
 * Laptop script pushes the current ngrok URL via adb:
 * adb shell am broadcast -n com.example.appfabricx/.telemetry.ApiUrlUpdateReceiver
 *   -a com.example.appfabricx.SET_API_URL --es base_url "https://....ngrok-free.app"
 */
class ApiUrlUpdateReceiver : BroadcastReceiver() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent?) {
        val url = intent?.getStringExtra(EXTRA_BASE_URL)?.trim().orEmpty()
        if (url.isBlank()) return
        val pending = goAsync()
        executor.execute {
            try {
                val app = context.applicationContext
                val config = TelemetryApiConfig(app)
                val uploader = TelemetryApiUploader(app)
                config.applyDiscoveredUrl(url)
                val sync = uploader.syncPending()
                Log.i(TAG, "API URL updated from broadcast: $url success=${sync.success}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply API URL from broadcast", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_API_URL = "com.example.appfabricx.SET_API_URL"
        const val EXTRA_BASE_URL = "base_url"
        private const val TAG = "ApiUrlUpdateReceiver"
    }
}
