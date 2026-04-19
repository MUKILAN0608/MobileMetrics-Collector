package com.example.appfabricx

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.*
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.Date
import java.util.Locale

class SystemMonitorService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var monitorThread: HandlerThread
    private lateinit var monitorHandler: Handler
    private var previousBatteryLevel: Int? = null
    private var previousBatteryTimeMs: Long? = null
    private var cpuSamplingAvailable = true
    private var cpuSamplingErrorLogged = false
    private var previousAppCpuTimeMs: Long? = null
    private var previousAppWallTimeMs: Long? = null

    private val monitorTask = object : Runnable {
        override fun run() {
            collectSystemData()
            monitorHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
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
                "System Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AppFabric Running")
            .setContentText("Monitoring system...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startMonitoring() {
        monitorHandler.post(monitorTask)
    }

    private fun collectSystemData() {
        val timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date()).toString()

        val cpuUsage = getCpuUsagePercent()

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        val usedMemoryMb = bytesToMb(usedMemory)
        val totalMemoryMb = bytesToMb(memoryInfo.totalMem)

        val batterySnapshot = getBatterySnapshot()
        val batteryLevel = batterySnapshot.first
        val temperatureC = batterySnapshot.second
        val batteryDrainRate = getBatteryDrainRatePerHour(batteryLevel)

        val foregroundApp = getForegroundAppPackageName()
        val runningAppsCount = getRunningAppsCount(activityManager)
        val processCount = getProcessCount(activityManager)

        val data = buildString {
            append("Timestamp: ").append(timestamp).append('\n')
            append("CPU usage: ").append(cpuUsage).append("%\n")
            append("Memory usage: ").append(String.format(Locale.US, "%.1f", usedMemoryMb))
                .append(" MB / ")
                .append(String.format(Locale.US, "%.1f", totalMemoryMb))
                .append(" MB\n")
            append("Battery: ").append(batteryLevel).append("%")
                .append(" | Drain: ").append(batteryDrainRate).append("\n")
            append("Temperature: ").append(temperatureC).append(" C\n")
            append("Foreground app: ").append(foregroundApp).append("\n")
            append("Running apps: ").append(runningAppsCount).append('\n')
            append("Process count: ").append(processCount)
        }

        sendToFlutter(data)
    }

    private fun getBatterySnapshot(): Pair<Int, String> {
        val intent = registerReceiver(null as BroadcastReceiver?, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temperatureTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        val percent = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        val temperature = if (temperatureTenths >= 0) {
            String.format(Locale.US, "%.1f", temperatureTenths / 10.0)
        } else {
            "N/A"
        }

        return Pair(percent, temperature)
    }

    private fun getBatteryDrainRatePerHour(currentBatteryLevel: Int): String {
        val now = System.currentTimeMillis()
        val previousLevel = previousBatteryLevel
        val previousTime = previousBatteryTimeMs

        previousBatteryLevel = currentBatteryLevel
        previousBatteryTimeMs = now

        if (previousLevel == null || previousTime == null || now <= previousTime) {
            return "calculating..."
        }

        val deltaLevel = previousLevel - currentBatteryLevel
        val elapsedHours = (now - previousTime) / 3600000.0
        if (elapsedHours <= 0.0) return "calculating..."

        return if (deltaLevel < 0) {
            "charging"
        } else {
            val rate = deltaLevel / elapsedHours
            String.format(Locale.US, "%.2f%%/h", rate)
        }
    }

    private fun getCpuUsagePercent(): String {
        if (!cpuSamplingAvailable) {
            val appCpu = getAppCpuUsagePercent()
            return if (appCpu >= 0f) {
                String.format(Locale.US, "%.2f (app)", appCpu)
            } else {
                "Restricted"
            }
        }
        val cpu = getCpuUsage()
        return if (cpu >= 0f) String.format(Locale.US, "%.2f", cpu) else "N/A"
    }

    private fun getAppCpuUsagePercent(): Float {
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

    private fun getCpuUsage(): Float {
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
                    Log.w("SystemMonitorService", "CPU sampling via /proc/stat is restricted on this device")
                }
                return -1f
            }
            if (!cpuSamplingErrorLogged) {
                cpuSamplingErrorLogged = true
                Log.w("SystemMonitorService", "Unable to read CPU stats", exception)
            }
            -1f
        }
    }

    private fun getForegroundAppPackageName(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return "Not supported"
        }

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )

        if (mode != AppOpsManager.MODE_ALLOWED) {
            return "Usage access not granted"
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestForegroundPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestForegroundPackage = event.packageName
            }
        }

        return latestForegroundPackage ?: "Unknown"
    }

    private fun getRunningAppsCount(activityManager: ActivityManager): Int {
        val packageNames = mutableSetOf<String>()
        activityManager.runningAppProcesses?.forEach { processInfo ->
            processInfo.pkgList?.forEach { packageName ->
                packageNames.add(packageName)
            }
        }
        return packageNames.size
    }

    private fun getProcessCount(activityManager: ActivityManager): Int {
        return activityManager.runningAppProcesses?.size ?: 0
    }

    private fun bytesToMb(bytes: Long): Double {
        return bytes / (1024.0 * 1024.0)
    }

    private fun sendToFlutter(data: String) {
        mainHandler.post {
            MainActivity.channel?.invokeMethod("updateData", data)
        }
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
}