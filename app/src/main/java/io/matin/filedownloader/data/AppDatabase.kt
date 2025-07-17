// Example AppDatabase.kt (if you don't have it set up this way)
package io.matin.filedownloader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WifiLogEntry::class, BatteryLogEntry::class, DownloadEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wifiLogDao(): WifiLogDao
    abstract fun downloadDao(): DownloadDao // <-- This must exist
    abstract fun batteryLogDao(): BatteryLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "file_downloader_database" // Your database name
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}