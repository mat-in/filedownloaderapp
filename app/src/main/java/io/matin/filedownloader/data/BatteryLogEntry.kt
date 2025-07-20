package io.matin.filedownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryPercentage: Float, // Calculated percentage (level * 100 / scale)
    val isCharging: Boolean,      // Derived from status
    val chargingStatus: Int,      // BatteryManager.BATTERY_STATUS_*
    val pluggedStatus: Int,       // BatteryManager.BATTERY_PLUGGED_*
    val healthStatus: Int,        // BatteryManager.BATTERY_HEALTH_*
    val voltage: Int,             // Millivolts
    val temperature: Int,         // Tenths of a degree Celsius
    val technology: String?,      // e.g., "Li-ion"
    val powerConsumptionAmps: Float? = null // This comes from download, not directly from ACTION_BATTERY_CHANGED
)