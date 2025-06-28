package io.matin.filedownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val fileUrl: String,
    val totalSize: Long,
    val downloadTimeMillis: Long,
    val downloadDate: Long = System.currentTimeMillis(),
    val fileUri: String? = null,
    val checksum: String? = null,
    val powerConsumptionAmps: Float? = null
)
