package com.example.appfabricx.data

import com.example.appfabricx.telemetry.RuntimeTelemetryCollector

/**
 * Canonical runtime row written to exactly one per-user table.
 */
data class RuntimeMetricRecord(
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryUsed: Long,
    val memoryAvailable: Long,
    val batteryPercentage: Int,
    val chargingStatus: String,
    val deviceTemperature: Float,
    val processCount: Int,
    val foregroundApp: String,
    val runningAppsCount: Int,
    val runtimeState: String,
) {
    fun isValid(): Boolean {
        return timestamp > 0L &&
            cpuUsage >= 0f &&
            memoryUsed >= 0L &&
            memoryAvailable >= 0L &&
            batteryPercentage in 0..100 &&
            chargingStatus.isNotBlank() &&
            deviceTemperature >= 0f &&
            processCount >= 0 &&
            foregroundApp.isNotBlank() &&
            runningAppsCount >= 0 &&
            runtimeState.isNotBlank()
    }

    fun toDevice1Entity() = Device1RuntimeMetric(
        timestamp = timestamp,
        cpuUsage = cpuUsage,
        memoryUsed = memoryUsed,
        memoryAvailable = memoryAvailable,
        batteryPercentage = batteryPercentage,
        chargingStatus = chargingStatus,
        deviceTemperature = deviceTemperature,
        processCount = processCount,
        foregroundApp = foregroundApp,
        runningAppsCount = runningAppsCount,
        runtimeState = runtimeState,
    )

    fun toDevice2Entity() = Device2RuntimeMetric(
        timestamp = timestamp,
        cpuUsage = cpuUsage,
        memoryUsed = memoryUsed,
        memoryAvailable = memoryAvailable,
        batteryPercentage = batteryPercentage,
        chargingStatus = chargingStatus,
        deviceTemperature = deviceTemperature,
        processCount = processCount,
        foregroundApp = foregroundApp,
        runningAppsCount = runningAppsCount,
        runtimeState = runtimeState,
    )

    fun toDevice3Entity() = Device3RuntimeMetric(
        timestamp = timestamp,
        cpuUsage = cpuUsage,
        memoryUsed = memoryUsed,
        memoryAvailable = memoryAvailable,
        batteryPercentage = batteryPercentage,
        chargingStatus = chargingStatus,
        deviceTemperature = deviceTemperature,
        processCount = processCount,
        foregroundApp = foregroundApp,
        runningAppsCount = runningAppsCount,
        runtimeState = runtimeState,
    )

    fun toMap(recordId: Long = 0): Map<String, Any?> = mapOf(
        "record_id" to recordId,
        "timestamp" to timestamp,
        "cpu_usage" to cpuUsage,
        "memory_used" to memoryUsed,
        "memory_available" to memoryAvailable,
        "battery_percentage" to batteryPercentage,
        "charging_status" to chargingStatus,
        "device_temperature" to deviceTemperature,
        "process_count" to processCount,
        "foreground_app" to foregroundApp,
        "running_apps_count" to runningAppsCount,
        "runtime_state" to runtimeState,
    )

    fun withRecordId(recordId: Long) = toMap(recordId)

    companion object {
        fun fromDevice1(entity: Device1RuntimeMetric) = fromFields(
            entity.timestamp, entity.cpuUsage, entity.memoryUsed, entity.memoryAvailable,
            entity.batteryPercentage, entity.chargingStatus, entity.deviceTemperature,
            entity.processCount, entity.foregroundApp, entity.runningAppsCount, entity.runtimeState,
        )

        fun fromDevice2(entity: Device2RuntimeMetric) = fromFields(
            entity.timestamp, entity.cpuUsage, entity.memoryUsed, entity.memoryAvailable,
            entity.batteryPercentage, entity.chargingStatus, entity.deviceTemperature,
            entity.processCount, entity.foregroundApp, entity.runningAppsCount, entity.runtimeState,
        )

        fun fromDevice3(entity: Device3RuntimeMetric) = fromFields(
            entity.timestamp, entity.cpuUsage, entity.memoryUsed, entity.memoryAvailable,
            entity.batteryPercentage, entity.chargingStatus, entity.deviceTemperature,
            entity.processCount, entity.foregroundApp, entity.runningAppsCount, entity.runtimeState,
        )

        private fun fromFields(
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
        ) = RuntimeMetricRecord(
            timestamp, cpuUsage, memoryUsed, memoryAvailable, batteryPercentage,
            chargingStatus, deviceTemperature, processCount, foregroundApp,
            runningAppsCount, runtimeState,
        )

        fun fromSnapshot(snapshot: RuntimeTelemetryCollector.Snapshot): RuntimeMetricRecord {
            return RuntimeMetricRecord(
                timestamp = snapshot.timestampMs,
                cpuUsage = snapshot.cpuUsagePercent,
                memoryUsed = snapshot.memoryUsedBytes,
                memoryAvailable = snapshot.memoryAvailableBytes,
                batteryPercentage = snapshot.batteryPercentage,
                chargingStatus = snapshot.chargingStatus,
                deviceTemperature = snapshot.deviceTemperatureC,
                processCount = snapshot.processCount,
                foregroundApp = snapshot.foregroundApp,
                runningAppsCount = snapshot.runningAppsCount,
                runtimeState = snapshot.runtimeState,
            )
        }
    }
}
