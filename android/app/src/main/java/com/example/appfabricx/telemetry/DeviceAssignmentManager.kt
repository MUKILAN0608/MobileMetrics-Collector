package com.example.appfabricx.telemetry

import android.content.Context
import com.example.appfabricx.data.RuntimeMetricSchema

/**
 * Maps this physical phone to exactly one isolated user/device table (1, 2, or 3).
 */
class DeviceAssignmentManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAssignedDevice(): Int {
        return prefs.getInt(KEY_DEVICE_SLOT, DEFAULT_DEVICE_SLOT)
    }

    fun setAssignedDevice(deviceSlot: Int) {
        val slot = deviceSlot.coerceIn(1, 3)
        prefs.edit().putInt(KEY_DEVICE_SLOT, slot).apply()
    }

    fun getTableName(): String = RuntimeMetricSchema.tableForDeviceSlot(getAssignedDevice())

    fun getUserLabel(): String {
        return prefs.getString(KEY_USER_LABEL, defaultUserLabel(getAssignedDevice()))
            ?: defaultUserLabel(getAssignedDevice())
    }

    fun setUserLabel(label: String) {
        prefs.edit().putString(KEY_USER_LABEL, label.trim()).apply()
    }

    /** Isolated mode: each user/phone writes only to their assigned table (default). */
    fun isIsolatedPerUserTables(): Boolean = !isMirrorToAllTables()

    fun isMirrorToAllTables(): Boolean {
        return prefs.getBoolean(KEY_MIRROR_ALL_TABLES, false)
    }

    fun setMirrorToAllTables(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIRROR_ALL_TABLES, enabled).apply()
    }

    private fun defaultUserLabel(slot: Int): String = "User $slot"

    companion object {
        const val PREFS_NAME = "appfabric_telemetry_prefs"
        const val KEY_DEVICE_SLOT = "assigned_device_slot"
        const val KEY_USER_LABEL = "assigned_user_label"
        const val KEY_MIRROR_ALL_TABLES = "mirror_all_tables"
        const val DEFAULT_DEVICE_SLOT = 1
    }
}
