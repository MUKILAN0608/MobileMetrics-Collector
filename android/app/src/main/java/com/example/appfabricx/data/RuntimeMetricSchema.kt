package com.example.appfabricx.data

/**
 * Canonical schema for all three per-user runtime metric tables.
 * Each team member/device has an isolated table with identical columns.
 */
object RuntimeMetricSchema {
    /** Single logical table on laptop API (all users / old + new uploads). */
    const val UNIFIED_SERVER_TABLE = "runtime_metrics"

    const val TABLE_DEVICE_1 = "device1_runtime_metrics"
    const val TABLE_DEVICE_2 = "device2_runtime_metrics"
    const val TABLE_DEVICE_3 = "device3_runtime_metrics"

    val COLUMNS = listOf(
        "record_id",
        "timestamp",
        "cpu_usage",
        "memory_used",
        "memory_available",
        "battery_percentage",
        "charging_status",
        "device_temperature",
        "process_count",
        "foreground_app",
        "running_apps_count",
        "runtime_state",
    )

    fun tableForDeviceSlot(deviceSlot: Int): String = when (deviceSlot.coerceIn(1, 3)) {
        1 -> TABLE_DEVICE_1
        2 -> TABLE_DEVICE_2
        else -> TABLE_DEVICE_3
    }

    fun deviceSlotForTable(tableName: String): Int = when (tableName) {
        TABLE_DEVICE_1 -> 1
        TABLE_DEVICE_2 -> 2
        TABLE_DEVICE_3 -> 3
        else -> 1
    }
}
