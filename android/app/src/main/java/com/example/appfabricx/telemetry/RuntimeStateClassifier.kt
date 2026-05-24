package com.example.appfabricx.telemetry

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PowerManager

/**
 * Classifies device runtime conditions from telemetry snapshots.
 */
class RuntimeStateClassifier(private val context: Context) {

    fun classify(
        cpuUsagePercent: Float,
        memoryUsedPercent: Float,
        batteryPercentage: Int,
        deviceTemperatureC: Float,
        isCharging: Boolean,
        foregroundAppPackage: String,
        runningAppsCount: Int,
        appSwitchesInInterval: Int,
    ): String {
        if (isGamingApp(foregroundAppPackage)) {
            return RuntimeState.GAMING_MODE
        }

        if (batteryPercentage < 15 || isBatterySaverEnabled()) {
            return RuntimeState.BATTERY_SAVER
        }

        if (deviceTemperatureC > 42f && cpuUsagePercent > 80f) {
            return RuntimeState.HIGH_LOAD
        }

        if (deviceTemperatureC > 40f) {
            return RuntimeState.THERMAL_RISK
        }

        if (memoryUsedPercent > 85f) {
            return RuntimeState.MULTITASKING_STRESS
        }

        if (cpuUsagePercent > 80f) {
            return RuntimeState.HIGH_LOAD
        }

        if (runningAppsCount > 25 || appSwitchesInInterval >= 3) {
            return RuntimeState.MULTITASKING_STRESS
        }

        return RuntimeState.NORMAL
    }

    private fun isBatterySaverEnabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    private fun isGamingApp(packageName: String): Boolean {
        if (packageName.isBlank() ||
            packageName == "Unknown" ||
            packageName == "Usage access not granted"
        ) {
            return false
        }

        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            if (info.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
            val label = context.packageManager.getApplicationLabel(info).toString().lowercase()
            label.contains("game") || packageName.contains(".game.")
        } catch (_: Exception) {
            false
        }
    }
}
