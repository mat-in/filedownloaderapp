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

// Removed @HiltWorker and @AssistedInject
class DownloadWorker constructor(
    private val appContext: Context,
    workerParams: WorkerParameters,
    // Dependencies are now passed directly by our custom factory
    private val fileDownloadRepository: FileDownloadRepository,
    private val notificationManager: DownloadNotificationManager,
    private val fileStorageHelper: FileStorageHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILE_URL = "file_url"
        const val KEY_FILE_NAME = "file_name"
        private const val TAG = "DownloadWorker"
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "Unknown File"
        val notification = notificationManager.buildInitialProgressNotification(fileName, 0)

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

        setForeground(getForegroundInfo())

        var downloadSuccess = false
        try {
            val progressListener = object : ProgressResponseBody.ProgressListener {
                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean, percentage: Int) {
                    val currentFivePercentBlock = percentage / 5 * 5

                    // Launch a new coroutine because `update` is called from OkHttp's I/O thread,
                    // and `setProgress` and `setForeground` are suspend functions.
                    CoroutineScope(Dispatchers.Default).launch {
                        runCatching {
                            // Corrected typo: lastFivePercentBlockReported -> lastReportedFivePercent
                            if (currentFivePercentBlock > (lastReportedFivePercent ?: -1) || (percentage == 100 && (lastReportedFivePercent ?: -1) != 100)) {
                                Log.d(TAG, "Reporting progress: $currentFivePercentBlock%")
                                notificationManager.showProgressNotification(fileName, currentFivePercentBlock)

                                val progressData = workDataOf("progress" to currentFivePercentBlock)
                                setProgress(progressData)

                                setForeground(ForegroundInfo(
                                    DownloadNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                                    notificationManager.buildInitialProgressNotification(fileName, currentFivePercentBlock),
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                                ))
                                lastReportedFivePercent = currentFivePercentBlock
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Error updating progress: ${e.message}", e)
                        }
                    }
                }
            }
            lastReportedFivePercent = null // Reset for this worker instance

            val rawResponseBody: ResponseBody = fileDownloadRepository.downloadFile(fileUrl)
            val progressTrackingResponseBody = ProgressResponseBody(rawResponseBody, progressListener)

            val savedUri = fileStorageHelper.saveFileToMediaStore(progressTrackingResponseBody, fileName)

            downloadSuccess = (savedUri != null)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $fileUrl: ${e.message}", e)
            notificationManager.showDownloadFailed(fileName, e.message ?: "Unknown error")
            return Result.failure(workDataOf("error_message" to e.message))
        }

        if (downloadSuccess) {
            Log.d(TAG, "Download and save successful for $fileName")
            notificationManager.showDownloadComplete(fileName)
            return Result.success(workDataOf("file_name" to fileName, "file_uri" to "TODO_URI"))
        } else {
            Log.e(TAG, "File saving failed for $fileName")
            notificationManager.showDownloadFailed(fileName, "File saving failed.")
            return Result.failure(workDataOf("error_message" to "File saving failed"))
        }
    }

    @Volatile
    private var lastReportedFivePercent: Int? = null


    // Helper function to extract a suitable filename from a URL
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