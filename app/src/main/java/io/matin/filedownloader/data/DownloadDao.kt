package io.matin.filedownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(downloadEntry: DownloadEntry): Long

    @Query("SELECT * FROM downloads ORDER BY downloadDate DESC")
    suspend fun getAllDownloads(): List<DownloadEntry>

    @Query("SELECT * FROM downloads WHERE fileName = :fileName LIMIT 1")
    suspend fun getDownloadByFileName(fileName: String): DownloadEntry?
}