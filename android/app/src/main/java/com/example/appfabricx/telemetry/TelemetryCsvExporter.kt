package com.example.appfabricx.telemetry

import android.content.Context
import com.example.appfabricx.data.AppDatabase
import com.example.appfabricx.data.Device1RuntimeMetric
import com.example.appfabricx.data.Device2RuntimeMetric
import com.example.appfabricx.data.Device3RuntimeMetric
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Auto-exports all three device tables to app-accessible storage (pull via adb).
 */
class TelemetryCsvExporter(context: Context) {

    private val dao = AppDatabase.getInstance(context).runtimeMetricDao()
    private val exportDir = File(
        context.getExternalFilesDir(null),
        "telemetry_export",
    ).apply { mkdirs() }

    fun exportAllTables(limitPerDevice: Int = 200) {
        writeDeviceCsv(1, dao.recentDevice1(limitPerDevice))
        writeDeviceCsv(2, dao.recentDevice2(limitPerDevice))
        writeDeviceCsv(3, dao.recentDevice3(limitPerDevice))

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val combined = File(exportDir, "all_devices_$timestamp.csv")
        combined.bufferedWriter().use { writer ->
            writer.appendLine(CSV_HEADER)
            dao.recentDevice1(limitPerDevice).forEach { writer.appendLine(it.toCsvLine(1)) }
            dao.recentDevice2(limitPerDevice).forEach { writer.appendLine(it.toCsvLine(2)) }
            dao.recentDevice3(limitPerDevice).forEach { writer.appendLine(it.toCsvLine(3)) }
        }
    }

    private fun writeDeviceCsv(deviceSlot: Int, rows: List<*>) {
        val file = File(exportDir, "device${deviceSlot}_runtime_metrics_latest.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine(CSV_HEADER)
            rows.forEach { row ->
                when (row) {
                    is Device1RuntimeMetric -> writer.appendLine(row.toCsvLine(deviceSlot))
                    is Device2RuntimeMetric -> writer.appendLine(row.toCsvLine(deviceSlot))
                    is Device3RuntimeMetric -> writer.appendLine(row.toCsvLine(deviceSlot))
                }
            }
        }
    }

    fun getExportDirectoryPath(): String = exportDir.absolutePath

    companion object {
        private const val CSV_HEADER =
            "device_slot,record_id,timestamp,cpu_usage,memory_used,memory_available," +
                "battery_percentage,charging_status,device_temperature,process_count," +
                "foreground_app,running_apps_count,runtime_state"

        private fun Device1RuntimeMetric.toCsvLine(deviceSlot: Int) = csvLine(
            deviceSlot, recordId, timestamp, cpuUsage, memoryUsed, memoryAvailable,
            batteryPercentage, chargingStatus, deviceTemperature, processCount,
            foregroundApp, runningAppsCount, runtimeState,
        )

        private fun Device2RuntimeMetric.toCsvLine(deviceSlot: Int) = csvLine(
            deviceSlot, recordId, timestamp, cpuUsage, memoryUsed, memoryAvailable,
            batteryPercentage, chargingStatus, deviceTemperature, processCount,
            foregroundApp, runningAppsCount, runtimeState,
        )

        private fun Device3RuntimeMetric.toCsvLine(deviceSlot: Int) = csvLine(
            deviceSlot, recordId, timestamp, cpuUsage, memoryUsed, memoryAvailable,
            batteryPercentage, chargingStatus, deviceTemperature, processCount,
            foregroundApp, runningAppsCount, runtimeState,
        )

        private fun csvLine(
            deviceSlot: Int,
            recordId: Long,
            timestamp: Long,
            cpuUsage: Float,
            memoryUsed: Long,
            memoryAvailable: Long,
            batteryPercentage: Int,
            chargingStatus: String,
            deviceTemperature: Float,
            processCount: Int,
            foregroundApp: String,
            runningAppsCount: Int,
            runtimeState: String,
        ): String {
            val safeApp = foregroundApp.replace(",", ";")
            return "$deviceSlot,$recordId,$timestamp,$cpuUsage,$memoryUsed,$memoryAvailable," +
                "$batteryPercentage,$chargingStatus,$deviceTemperature,$processCount," +
                "$safeApp,$runningAppsCount,$runtimeState"
        }
    }
}
