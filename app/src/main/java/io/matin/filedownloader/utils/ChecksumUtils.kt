package io.matin.filedownloader.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utility object for calculating file checksums.
 */
object ChecksumUtils {

    private const val TAG = "ChecksumUtils"

    /**
     * Calculates the MD5 checksum of a given file.
     *
     * @param file The file for which to calculate the MD5 checksum.
     * @return The MD5 checksum as a hexadecimal string, or null if an error occurs.
     */
    fun calculateMD5(file: File): String? {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return null
        }
        if (!file.isFile) {
            Log.e(TAG, "Path is not a file: ${file.absolutePath}")
            return null
        }

        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating MD5 checksum for ${file.absolutePath}: ${e.message}", e)
            null
        }
    }
}
