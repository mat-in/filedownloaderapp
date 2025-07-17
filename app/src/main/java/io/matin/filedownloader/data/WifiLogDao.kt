package io.matin.filedownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WifiLogDao {
    @Insert
    suspend fun insertWifiLog(wifiLogEntry: WifiLogEntry)

    @Query("SELECT * FROM wifi_logs ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentWifiLogs(): List<WifiLogEntry>
}