// io.matin.filedownloader.data.BatteryLogDao.kt
package io.matin.filedownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryLog(log: BatteryLogEntry)

    // Changed to return Flow for reactive UI updates
    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC")
    fun getAllBatteryLogsFlow(): Flow<List<BatteryLogEntry>>

    // If you still need a one-shot read (e.g., for specific background tasks), keep this too:
    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC")
    suspend fun getAllBatteryLogs(): List<BatteryLogEntry>

    @Query("DELETE FROM battery_logs")
    suspend fun clearAllBatteryLogs()
}