package com.example.appfabricx.telemetry

/**
 * Tracks foreground application transitions for workload analytics.
 */
class ForegroundAppTracker {

    private var lastForegroundApp: String? = null
    private var switchesInCurrentInterval = 0
    private var totalSwitches = 0

    fun onForegroundAppChanged(currentApp: String): Int {
        val normalized = currentApp.trim()
        if (normalized.isEmpty() ||
            normalized == "Unknown" ||
            normalized == "Usage access not granted"
        ) {
            return switchesInCurrentInterval
        }

        val previous = lastForegroundApp
        if (previous != null && previous != normalized) {
            switchesInCurrentInterval++
            totalSwitches++
        }
        lastForegroundApp = normalized
        return switchesInCurrentInterval
    }

    fun consumeIntervalSwitchCount(): Int {
        val count = switchesInCurrentInterval
        switchesInCurrentInterval = 0
        return count
    }

    fun getTotalSwitches(): Int = totalSwitches

    fun getLastForegroundApp(): String? = lastForegroundApp
}
