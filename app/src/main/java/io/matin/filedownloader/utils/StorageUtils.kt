package io.matin.filedownloader.utils

import android.os.Environment
import android.os.StatFs
import java.text.DecimalFormat

object StorageUtils {

    /**
     * Converts a byte value to a human-readable string (e.g., 10 KB, 500 MB, 2.5 GB).
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.#")
        return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * Gets the total internal storage size.
     * Returns a Pair: first is total size in bytes, second is free space in bytes.
     */
    fun getInternalStorageInfo(): Pair<Long, Long> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalSize = totalBlocks * blockSize
        val freeSpace = availableBlocks * blockSize

        return Pair(totalSize, freeSpace)
    }

    /**
     * Gets the total external storage (e.g., SD card) size if available.
     * Note: On modern Android, external storage usually refers to the emulated internal storage.
     * For removable SD cards, additional checks for `Environment.MEDIA_MOUNTED` might be needed.
     * This method focuses on the primary external storage.
     * Returns a Pair: first is total size in bytes, second is free space in bytes.
     */
    fun getExternalStorageInfo(): Pair<Long, Long>? {
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalSize = totalBlocks * blockSize
            val freeSpace = availableBlocks * blockSize

            return Pair(totalSize, freeSpace)
        }
        return null
    }
}