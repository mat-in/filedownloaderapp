package io.matin.filedownloader.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.network.ProgressResponseBody
import io.matin.filedownloader.repo.FileDownloadRepository
import io.matin.filedownloader.notifications.DownloadNotificationManager
import java.net.URL
import android.Manifest
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import io.matin.filedownloader.data.DownloadEntry
import io.matin.filedownloader.data.DownloadDao
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val fileDownloadRepository: FileDownloadRepository,
    private val notificationManager: DownloadNotificationManager,
    private val fileStorageHelper: FileStorageHelper,
    private val downloadDao: DownloadDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILE_URL = "file_url"
        const val KEY_FILE_NAME = "file_name"
        private const val TAG = "DownloadWorker"
    }

    @Volatile
    private var lastReportedFivePercent: Int? = null

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "Unknown File"
        // Ensure initial progress is correctly set for the foreground notification
        val initialProgress = lastReportedFivePercent ?: 0
        val notification = notificationManager.buildInitialProgressNotification(fileName, initialProgress)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                DownloadNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                notification
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString(KEY_FILE_URL) ?: return Result.failure(workDataOf("error_message" to "File URL is missing"))
        val fileName = inputData.getString(KEY_FILE_NAME) ?: getFileNameFromUrl(fileUrl)

        Log.d(TAG, "Starting download for $fileUrl with filename $fileName")


        lastReportedFivePercent = null


        setForeground(getForegroundInfo())

        var downloadSuccess = false
        var totalDownloadedSize: Long = 0
        val startTime = System.currentTimeMillis()

        try {
            val progressListener = object : ProgressResponseBody.ProgressListener {
                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean, percentage: Int) {
                    totalDownloadedSize = bytesRead


                    coroutineContext.ensureActive()

                    val currentFivePercentBlock = percentage / 5 * 5


                    CoroutineScope(Dispatchers.Default).launch {
                        runCatching {
                            if (currentFivePercentBlock > (lastReportedFivePercent ?: -1) || (percentage == 100 && (lastReportedFivePercent ?: -1) != 100)) {
                                Log.d(TAG, "Reporting progress: $currentFivePercentBlock%")

                                notificationManager.showProgressNotification(fileName, currentFivePercentBlock)

                                val progressData = workDataOf("progress" to currentFivePercentBlock)

                                this@DownloadWorker.setProgress(progressData)


                                lastReportedFivePercent = currentFivePercentBlock
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Error updating progress or notification: ${e.message}", e)
                        }
                    }
                }
            }


            val response = withContext(Dispatchers.IO) {
                fileDownloadRepository.downloadFile(fileUrl)
            }

            if (!response.isSuccessful) {
                val errorMessage = "Download failed: HTTP ${response.code} ${response.message}"
                Log.e(TAG, errorMessage)
                notificationManager.showDownloadFailed(fileName, errorMessage)
                return Result.failure(workDataOf("error_message" to errorMessage))
            }

            val rawResponseBody: ResponseBody = response.body
                ?: throw java.io.IOException("Response body is null for URL: $fileUrl")

            val progressTrackingResponseBody = ProgressResponseBody(rawResponseBody, progressListener)


            val savedUri = withContext(Dispatchers.IO) {
                fileStorageHelper.saveFileToMediaStore(progressTrackingResponseBody, fileName)
            }

            downloadSuccess = (savedUri != null)

            if (downloadSuccess) {
                val endTime = System.currentTimeMillis()
                val downloadDuration = endTime - startTime

                val downloadEntry = DownloadEntry(
                    fileName = fileName,
                    fileUrl = fileUrl,
                    totalSize = totalDownloadedSize,
                    downloadTimeMillis = downloadDuration
                )

                withContext(Dispatchers.IO) {
                    downloadDao.insertDownload(downloadEntry)
                }
                Log.d(TAG, "Download metrics saved to DB: $downloadEntry")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $fileUrl: ${e.message}", e)
            notificationManager.showDownloadFailed(fileName, e.message ?: "Unknown error")
            return Result.failure(workDataOf("error_message" to e.message))
        } finally {

            if (downloadSuccess) {
                notificationManager.showDownloadComplete(fileName)
            } else {
                notificationManager.cancelNotification()
            }
        }

        if (downloadSuccess) {
            Log.d(TAG, "Download and save successful for $fileName")
            return Result.success(workDataOf("file_name" to fileName, "file_uri" to "TODO_URI"))
        } else {
            Log.e(TAG, "File saving failed for $fileName")
            return Result.failure(workDataOf("error_message" to "File saving failed"))
        }
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            val path = URL(url).path
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment.isNotBlank()) lastSegment else "downloaded_file_${System.currentTimeMillis()}.bin"
        } catch (e: Exception) {
            "downloaded_file_${System.currentTimeMillis()}.bin"
        }
    }
}