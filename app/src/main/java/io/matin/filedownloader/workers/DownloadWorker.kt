package io.matin.filedownloader.workers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.matin.filedownloader.data.BatteryLogDao
import io.matin.filedownloader.data.BatteryLogEntry
import io.matin.filedownloader.data.DownloadDao
import io.matin.filedownloader.data.DownloadEntry
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.network.ProgressResponseBody
import io.matin.filedownloader.notifications.DownloadNotificationManager
import io.matin.filedownloader.repo.FileDownloadRepository
import io.matin.filedownloader.repo.encodeURLParameter
import io.matin.filedownloader.utils.ChecksumUtils
import io.matin.filedownloader.utils.ErrorLogCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val fileDownloadRepository: FileDownloadRepository,
    private val notificationManager: DownloadNotificationManager,
    private val fileStorageHelper: FileStorageHelper,
    private val downloadDao: DownloadDao,
    private val batteryLogDao: BatteryLogDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_LENGTH = "file_length"
        const val KEY_CHECKSUM = "checkSum"
        const val KEY_POWER_CONSUMPTION_AMPS = "power_consumption_amps"
        const val KEY_BASE_URL = "base_url"
        private const val TAG = "DownloadWorker"
    }

    @Volatile
    private var lastReportedFivePercent: Int? = null
    private lateinit var tempDownloadFile: File

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "Unknown File"
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
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure(workDataOf("error_message" to "File Name is missing"))
        val fileLength = inputData.getLong(KEY_FILE_LENGTH, -1L)
        val checkSum = inputData.getString(KEY_CHECKSUM)
        val baseUrl = inputData.getString(KEY_BASE_URL)
            ?: return Result.failure(workDataOf("error_message" to "Base URL is missing in worker input data."))

        fileDownloadRepository.setBaseUrl(baseUrl)

        val downloadUrlForDb = "$baseUrl/getFile/${fileName.encodeURLParameter()}"

        Log.d(TAG, "Starting download for $fileName (via backend /getFile/{name} endpoint) from $baseUrl")

        lastReportedFivePercent = null

        val tempDir = File(applicationContext.cacheDir, "downloads")
        if (!tempDir.exists()) tempDir.mkdirs()
        tempDownloadFile = File(tempDir, "$fileName.tmp")

        var startByte = 0L
        if (tempDownloadFile.exists()) {
            startByte = tempDownloadFile.length()
            Log.d(TAG, "Resuming download from byte: $startByte for $fileName")
        }

        setForeground(getForegroundInfo())

        var downloadSuccess = false
        var totalDownloadedSize: Long = startByte
        val startTime = System.currentTimeMillis()
        var outputUriString: String? = null
        // Changed to store instantaneous current, not a calculated difference
        var instantaneousBatteryCurrentMicroAmps: Long? = null

        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        try {
            instantaneousBatteryCurrentMicroAmps = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            Log.d(TAG, "Instantaneous Battery Current (Start): ${instantaneousBatteryCurrentMicroAmps / 1000.0f} mA")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission BATTERY_STATS not granted. Cannot read battery current. ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting instantaneous battery current: ${e.message}")
        }

        try {
            val progressListener = object : ProgressResponseBody.ProgressListener {
                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean, percentage: Int) {
                    val currentDownloadedBytes = startByte + bytesRead
                    totalDownloadedSize = currentDownloadedBytes

                    coroutineContext.ensureActive()

                    val actualContentLength = if (contentLength != -1L) contentLength else fileLength
                    val currentPercentage = if (actualContentLength > 0 && actualContentLength != -1L) {
                        ((currentDownloadedBytes.toFloat() / actualContentLength) * 100).toInt().coerceIn(0, 100)
                    } else if (percentage != 0) {
                        percentage
                    } else {
                        0
                    }

                    val currentFivePercentBlock = (currentPercentage / 5) * 5

                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            if (currentFivePercentBlock > (lastReportedFivePercent ?: -1) || (currentPercentage == 100 && (lastReportedFivePercent ?: -1) != 100)) {
                                Log.d(TAG, "Reporting progress for $fileName: $currentFivePercentBlock%")
                                notificationManager.showProgressNotification(fileName, currentFivePercentBlock)
                                val progressData = workDataOf("progress" to currentFivePercentBlock)
                                this@DownloadWorker.setProgress(progressData)
                                lastReportedFivePercent = currentFivePercentBlock
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Error updating progress or notification for $fileName: ${e.message}", e)
                        }
                    }
                }
            }

            val response = fileDownloadRepository.downloadFileFromBackend(fileName, startByte)

            if (!response.isSuccessful) {
                ErrorLogCollector.logError("res", "unsuccessful response")
                if (response.code == 416 && startByte > 0) {
                    Log.d(TAG, "Received 416 (Range Not Satisfiable) for $fileName. Assuming file is already complete on client side.")
                    downloadSuccess = true
                } else {
                    val errorMessage = "Download failed: HTTP ${response.code} ${response.message}"
                    Log.e(TAG, errorMessage)

                    notificationManager.showDownloadFailed(fileName, errorMessage)
                    return Result.failure(workDataOf("error_message" to errorMessage))
                }
            }

            if (!downloadSuccess) {
                val rawResponseBody: ResponseBody = response.body
                    ?: throw java.io.IOException("Response body is null for file: $fileName")

                val progressTrackingResponseBody = ProgressResponseBody(rawResponseBody, progressListener)

                val appendMode = startByte > 0 && response.code == 206
                if (!appendMode && startByte > 0) {
                    Log.w(TAG, "Server did not return 206 for range request. Overwriting existing temporary file.")
                    tempDownloadFile.delete()
                    startByte = 0L
                    totalDownloadedSize = 0L
                }

                FileOutputStream(tempDownloadFile, appendMode).use { fileOutputStream ->
                    progressTrackingResponseBody.source().use { source ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (source.read(buffer).also { bytesRead = it } != -1) {
                            fileOutputStream.write(buffer, 0, bytesRead)
                            coroutineContext.ensureActive()
                        }
                    }
                }
                Log.d(TAG, "Temporary download to ${tempDownloadFile.absolutePath} completed for $fileName.")
                downloadSuccess = true
                ErrorLogCollector.logError("downloadworker", "temp file created")
            }

            if (downloadSuccess) {
                Log.d(TAG, "Power consumption logged as instantaneous current at start: ${instantaneousBatteryCurrentMicroAmps?.div(1_000_000.0f)} Amps (raw microamps: $instantaneousBatteryCurrentMicroAmps)")

                if (!checkSum.isNullOrBlank()) {
                    try {
                        val actualChecksum = withContext(Dispatchers.IO) {
                            ChecksumUtils.calculateMD5(tempDownloadFile)
                        }
                        if (actualChecksum != checkSum) {
                            val errorMessage = "Checksum mismatch for $fileName. Expected $checkSum, got $actualChecksum."
                            Log.e(TAG, errorMessage)
                            notificationManager.showDownloadFailed(fileName, errorMessage)
                            return Result.failure(workDataOf("error_message" to errorMessage))
                        } else {
                            Log.d(TAG, "Checksum verified successfully for $fileName.")
                        }
                    } catch (e: Exception) {
                        val errorMessage = "Error calculating checksum for $fileName: ${e.message}"
                        Log.e(TAG, errorMessage, e)
                        notificationManager.showDownloadFailed(fileName, errorMessage)
                        return Result.failure(workDataOf("error_message" to errorMessage))
                    }
                }

                val fileInputStream = tempDownloadFile.inputStream()
                val tempFileContentLength = tempDownloadFile.length()

                val finalResponseBody = object : ResponseBody() {
                    override fun contentLength(): Long = tempFileContentLength
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun source(): okio.BufferedSource = fileInputStream.source().buffer()
                }

                val savedUri = withContext(Dispatchers.IO) {
                    fileStorageHelper.saveFileToMediaStore(finalResponseBody, fileName)
                }

                fileInputStream.close()
                tempDownloadFile.delete()

                if (savedUri != null) {
                    val endTime = System.currentTimeMillis()
                    val downloadDuration = endTime - startTime

                    outputUriString = savedUri.toString()

                    val downloadEntry = DownloadEntry(
                        fileName = fileName,
                        fileUrl = downloadUrlForDb,
                        totalSize = tempFileContentLength,
                        downloadTimeMillis = downloadDuration,
                        fileUri = outputUriString,
                        checksum = checkSum,
                        powerConsumptionAmps = instantaneousBatteryCurrentMicroAmps?.div(1_000_000.0f) // Store as Amps or null
                    )

                    val insertedDownloadId = withContext(Dispatchers.IO) {
                        downloadDao.insertDownload(downloadEntry)
                    }
                    Log.d(TAG, "Download metrics saved to DB: $downloadEntry")

                    val batteryStatus: Intent? = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

                    val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val currentBatteryPercentage: Float = if (scale > 0) (level * 100 / scale.toFloat()) else 0.0f

                    val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    val pluggedStatus: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                    val healthStatus: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
                    val voltage: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
                    val temperature: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                    val technology: String? = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

                    val batteryLogEntry = BatteryLogEntry(
                        batteryPercentage = currentBatteryPercentage,
                        isCharging = isCharging,
                        chargingStatus = status,
                        pluggedStatus = pluggedStatus,
                        healthStatus = healthStatus,
                        voltage = voltage,
                        temperature = temperature,
                        technology = technology,
                        powerConsumptionAmps = instantaneousBatteryCurrentMicroAmps?.div(1_000_000.0f)
                    )
                    withContext(Dispatchers.IO) {
                        batteryLogDao.insertBatteryLog(batteryLogEntry)
                    }
                    Log.d(TAG, "Battery metrics saved to DB: $batteryLogEntry")

                    val successResult = fileDownloadRepository.sendDownloadSuccess(fileName)
                    successResult.onSuccess { msg ->
                        Log.d(TAG, "Backend reported success for $fileName: $msg")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to report success to backend for $fileName: ${e.message}", e)
                    }

                } else {
                    downloadSuccess = false
                    Log.e(TAG, "Failed to save file to MediaStore after download for $fileName.")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $fileName: ${e.message}", e)
            notificationManager.showDownloadFailed(fileName, e.message ?: "Unknown error")
            return Result.failure(workDataOf("error_message" to e.message))
        } finally {
            if (downloadSuccess) {
                notificationManager.showDownloadComplete(fileName)
            } else {
                notificationManager.cancelNotification()
            }

            if (tempDownloadFile.exists()) {
                Log.d(TAG, "Cleaning up temporary file: ${tempDownloadFile.absolutePath}")
                tempDownloadFile.delete()
            }
        }

        return if (downloadSuccess) {
            Log.d(TAG, "Download and save successful for $fileName")
            Result.success(workDataOf(
                "file_name" to fileName,
                "file_uri" to (outputUriString ?: ""),
                // Pass the instantaneous raw value (converted to Amps or -1.0f if null)
                KEY_POWER_CONSUMPTION_AMPS to (instantaneousBatteryCurrentMicroAmps?.div(1_000_000.0f) ?: -1.0f)
            ))
        } else {
            Log.e(TAG, "File saving failed or download encountered an unrecoverable error for $fileName")
            Result.failure(workDataOf("error_message" to "File saving failed or download error"))
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