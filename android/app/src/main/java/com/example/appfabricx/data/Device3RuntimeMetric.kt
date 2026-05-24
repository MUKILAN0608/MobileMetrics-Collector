package com.example.appfabricx.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device3_runtime_metrics")
data class Device3RuntimeMetric(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "record_id")
    val recordId: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "cpu_usage")
    val cpuUsage: Float,
    @ColumnInfo(name = "memory_used")
    val memoryUsed: Long,
    @ColumnInfo(name = "memory_available")
    val memoryAvailable: Long,
    @ColumnInfo(name = "battery_percentage")
    val batteryPercentage: Int,
    @ColumnInfo(name = "charging_status")
    val chargingStatus: String,
    @ColumnInfo(name = "device_temperature")
    val deviceTemperature: Float,
    @ColumnInfo(name = "process_count")
    val processCount: Int,
    @ColumnInfo(name = "foreground_app")
    val foregroundApp: String,
    @ColumnInfo(name = "running_apps_count")
    val runningAppsCount: Int,
    @ColumnInfo(name = "runtime_state")
    val runtimeState: String,
)
