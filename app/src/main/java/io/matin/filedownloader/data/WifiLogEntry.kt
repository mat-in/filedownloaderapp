package io.matin.filedownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_logs")
data class WifiLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val rssi: Int, // Received Signal Strength Indication (dBm)
    val linkSpeedMbps: Int, // Link speed in Mbps
    val wifiStandard: Int, // Wi-Fi standard (e.g., WifiInfo.WIFI_STANDARD_11AC)
    val frequencyMHz: Int, // Frequency in MHz
    val timestamp: Long = System.currentTimeMillis()
)