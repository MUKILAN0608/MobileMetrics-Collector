package com.example.appfabricx.telemetry

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.random.Random

/**
 * Collects a single structured runtime telemetry snapshot from the device.
 */
class RuntimeTelemetryCollector(private val context: Context) {

    private var cpuSamplingAvailable = true
    private var cpuSamplingErrorLogged = false
    private var previousAppCpuTimeMs: Long? = null
    private var previousAppWallTimeMs: Long? = null

    data class Snapshot(
        val timestampMs: Long,
        val cpuUsagePercent: Float,
        val memoryUsedBytes: Long,
        val memoryAvailableBytes: Long,
        val memoryUsedPercent: Float,
        val batteryPercentage: Int,
        val chargingStatus: String,
        val isCharging: Boolean,
        val deviceTemperatureC: Float,
        val processCount: Int,
        val foregroundApp: String,
        val runningAppsCount: Int,
        val appSwitchesInInterval: Int,
        val runtimeState: String,
    )

    fun collect(
        stateClassifier: RuntimeStateClassifier,
        foregroundTracker: ForegroundAppTracker,
    ): Snapshot {
        val timestampMs = System.currentTimeMillis()
        val cpuUsage = readCpuUsagePercent()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val memoryUsed = memoryInfo.totalMem - memoryInfo.availMem
        val memoryAvailable = memoryInfo.availMem
        val memoryUsedPercent = if (memoryInfo.totalMem > 0L) {
            (memoryUsed.toFloat() / memoryInfo.totalMem.toFloat()) * 100f
        } else {
            0f
        }

        val battery = readBattery()
        val foregroundApp = resolveForegroundAppLabel()
        foregroundTracker.onForegroundAppChanged(foregroundApp)
        val appSwitches = foregroundTracker.consumeIntervalSwitchCount()

        val processCount = activityManager.runningAppProcesses?.size ?: 0
        val runningAppsCount = countRunningApps(activityManager)

        val runtimeState = stateClassifier.classify(
            cpuUsagePercent = cpuUsage,
            memoryUsedPercent = memoryUsedPercent,
            batteryPercentage = battery.percentage,
            deviceTemperatureC = battery.temperatureC,
            isCharging = battery.isCharging,
            foregroundAppPackage = foregroundApp,
            runningAppsCount = runningAppsCount,
            appSwitchesInInterval = appSwitches,
        )

        return Snapshot(
            timestampMs = timestampMs,
            cpuUsagePercent = cpuUsage,
            memoryUsedBytes = memoryUsed,
            memoryAvailableBytes = memoryAvailable,
            memoryUsedPercent = memoryUsedPercent,
            batteryPercentage = battery.percentage,
            chargingStatus = battery.chargingStatus,
            isCharging = battery.isCharging,
            deviceTemperatureC = battery.temperatureC,
            processCount = processCount,
            foregroundApp = foregroundApp,
            runningAppsCount = runningAppsCount,
            appSwitchesInInterval = appSwitches,
            runtimeState = runtimeState,
        )
    }

    fun nextCollectionDelayMs(): Long {
        return Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)
    }

    private data class BatterySnapshot(
        val percentage: Int,
        val temperatureC: Float,
        val chargingStatus: String,
        val isCharging: Boolean,
    )

    private fun readBattery(): BatterySnapshot {
        val intent = context.registerReceiver(
            null as BroadcastReceiver?,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temperatureTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percent = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        val temperatureC = if (temperatureTenths >= 0) {
            temperatureTenths / 10f
        } else {
            0f
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val chargingStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> if (isCharging) "CHARGING" else "UNKNOWN"
        }

        return BatterySnapshot(
            percentage = percent.coerceIn(0, 100),
            temperatureC = temperatureC,
            chargingStatus = chargingStatus,
            isCharging = isCharging,
        )
    }

    private fun readCpuUsagePercent(): Float {
        if (!cpuSamplingAvailable) {
            val appCpu = readAppCpuUsagePercent()
            return if (appCpu >= 0f) appCpu else 0f
        }

        val cpu = readSystemCpuUsagePercent()
        return if (cpu >= 0f) cpu else readAppCpuUsagePercent().coerceAtLeast(0f)
    }

    private fun readAppCpuUsagePercent(): Float {
        val nowCpuMs = Process.getElapsedCpuTime()
        val nowWallMs = SystemClock.elapsedRealtime()

        val prevCpuMs = previousAppCpuTimeMs
        val prevWallMs = previousAppWallTimeMs

        previousAppCpuTimeMs = nowCpuMs
        previousAppWallTimeMs = nowWallMs

        if (prevCpuMs == null || prevWallMs == null) return -1f

        val deltaCpuMs = nowCpuMs - prevCpuMs
        val deltaWallMs = nowWallMs - prevWallMs
        if (deltaWallMs <= 0L) return -1f

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val usage = (deltaCpuMs.toFloat() / (deltaWallMs.toFloat() * cores)) * 100f
        return usage.coerceIn(0f, 100f)
    }

    private fun readSystemCpuUsagePercent(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load1 = reader.readLine()
            val toks1 = load1.split("\\s+".toRegex())
            if (toks1.size < 8) {
                reader.close()
                return -1f
            }

            val idle1 = toks1[4].toLong()
            val cpu1 = toks1[1].toLong() + toks1[2].toLong() +
                toks1[3].toLong() + toks1[5].toLong() +
                toks1[6].toLong() + toks1[7].toLong()

            Thread.sleep(360)

            reader.seek(0)
            val load2 = reader.readLine()
            val toks2 = load2.split("\\s+".toRegex())
            if (toks2.size < 8) {
                reader.close()
                return -1f
            }

            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[1].toLong() + toks2[2].toLong() +
                toks2[3].toLong() + toks2[5].toLong() +
                toks2[6].toLong() + toks2[7].toLong()

            reader.close()

            val cpuDiff = cpu2 - cpu1
            val idleDiff = idle2 - idle1
            if (cpuDiff + idleDiff <= 0L) return -1f

            ((cpuDiff.toFloat() / (cpuDiff + idleDiff)) * 100f).coerceIn(0f, 100f)
        } catch (exception: Exception) {
            if (exception is FileNotFoundException && exception.message?.contains("EACCES") == true) {
                cpuSamplingAvailable = false
                if (!cpuSamplingErrorLogged) {
                    cpuSamplingErrorLogged = true
                    Log.w(TAG, "CPU sampling via /proc/stat is restricted on this device")
                }
                return -1f
            }
            if (!cpuSamplingErrorLogged) {
                cpuSamplingErrorLogged = true
                Log.w(TAG, "Unable to read CPU stats", exception)
            }
            -1f
        }
    }

    private fun resolveForegroundAppLabel(): String {
        val packageName = resolveForegroundAppPackage()
        if (packageName == "Usage access not granted" || packageName == "Unknown") {
            return packageName
        }

        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val label = context.packageManager.getApplicationLabel(info).toString()
            "$label ($packageName)"
        } catch (_: Exception) {
            packageName
        }
    }

    private fun resolveForegroundAppPackage(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return "Not supported"
        }

        if (!hasUsageAccess()) {
            return "Usage access not granted"
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()

        val fromEvents = resolveFromUsageEvents(usageStatsManager, endTime)
        if (fromEvents != null && fromEvents != "Unknown") {
            return fromEvents
        }

        return resolveFromUsageStats(usageStatsManager, endTime) ?: "Unknown"
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun resolveFromUsageEvents(
        usageStatsManager: UsageStatsManager,
        endTime: Long,
    ): String? {
        val startTime = endTime - 60_000
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return null
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val isForegroundEvent = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                UsageEvents.Event.ACTIVITY_RESUMED -> true
                else -> false
            }
            if (isForegroundEvent && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }

        return latestPackage
    }

    private fun resolveFromUsageStats(
        usageStatsManager: UsageStatsManager,
        endTime: Long,
    ): String? {
        val startTime = endTime - 120_000
        val statsList: List<UsageStats> = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime,
        ) ?: return null

        if (statsList.isEmpty()) return null

        val ownPackage = context.packageName
        val candidate = statsList
            .filter { it.packageName != ownPackage && it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?: return null

        // Only trust if used within the last 2 minutes
        if (endTime - candidate.lastTimeUsed > 120_000) {
            return null
        }

        return candidate.packageName
    }

    private fun countRunningApps(activityManager: ActivityManager): Int {
        val packageNames = mutableSetOf<String>()
        activityManager.runningAppProcesses?.forEach { processInfo ->
            processInfo.pkgList?.forEach { packageName ->
                packageNames.add(packageName)
            }
        }
        return packageNames.size
    }

    companion object {
        private const val TAG = "RuntimeTelemetryCollector"
        private const val MIN_INTERVAL_MS = 2_000L
        private const val MAX_INTERVAL_MS = 5_000L
    }
}
