package io.matin.filedownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val fileUrl: String,
    val totalSize: Long, // in bytes
    val downloadTimeMillis: Long, // time taken to download in milliseconds
    val downloadDate: Long = System.currentTimeMillis() // timestamp of download completion
)