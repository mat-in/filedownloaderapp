package io.matin.filedownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BatteryLogDao {
    @Insert
    suspend fun insertBatteryLog(batteryLogEntry: BatteryLogEntry)

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC")
    suspend fun getAllBatteryLogs(): List<BatteryLogEntry>
}