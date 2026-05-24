package com.example.appfabricx.telemetry

data class PersistResult(
    val success: Boolean,
    val lastRecordId: Long,
    val writtenTable: String,
    val writtenTables: List<String>,
    val totalDevice1: Int,
    val totalDevice2: Int,
    val totalDevice3: Int,
    val assignedDeviceSlot: Int = 1,
    val userLabel: String = "User 1",
    val errorMessage: String? = null,
    val apiSync: Map<String, Any?>? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "success" to success,
        "last_record_id" to lastRecordId,
        "written_table" to writtenTable,
        "written_tables" to writtenTables,
        "total_device1" to totalDevice1,
        "total_device2" to totalDevice2,
        "total_device3" to totalDevice3,
        "assigned_device_slot" to assignedDeviceSlot,
        "user_label" to userLabel,
        "error_message" to errorMessage,
        "api_sync" to apiSync,
    )
}
