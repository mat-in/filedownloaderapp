package io.matin.filedownloader.filestorage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "FileStorageHelper"

    suspend fun saveFileToMediaStore(responseBody: ResponseBody, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            val mimeType = getMimeTypeFromFileName(fileName)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator + "MyFileDownloads")
                } else {
                    val appSpecificExternalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(appSpecificExternalDir, "MyFileDownloads" + File.separator + fileName)
                    put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            var uri: Uri? = null
            try {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.flush()
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(fileUri, contentValues, null, null)
                    }

                    Log.d(TAG, "File saved to MediaStore URI: $fileUri")
                    fileUri
                } ?: run {
                    Log.e(TAG, "Failed to get MediaStore URI for saving file.")
                    null
                }
            } catch (e: Exception) {
                uri?.let { resolver.delete(it, null, null) }
                Log.e(TAG, "Error saving file to MediaStore: ${e.message}", e)
                null
            }
        }
    }

    private fun getMimeTypeFromFileName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}