package io.matin.filedownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val downloadId: Long? = null,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val powerConsumptionAmps: Float?,
    val timestamp: Long = System.currentTimeMillis()
)