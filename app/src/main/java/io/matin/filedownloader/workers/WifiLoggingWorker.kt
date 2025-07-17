package io.matin.filedownloader.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.matin.filedownloader.data.AppDatabase // Assuming your Room database class is named AppDatabase
import io.matin.filedownloader.data.WifiLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WifiLoggingWorker (
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // You will get the DAO instance inside doWork() or lazy-initialized property
    // as you cannot pass it directly in the constructor of a non-Hilt Worker
    private val wifiLogDao = AppDatabase.getInstance(appContext).wifiLogDao()

    companion object {
        const val KEY_RSSI = "rssi"
        const val KEY_LINK_SPEED = "link_speed"
        const val KEY_WIFI_STANDARD = "wifi_standard"
        const val KEY_FREQUENCY = "frequency"
        private const val TAG = "WifiLoggingWorker"
    }

    override suspend fun doWork(): Result {
        val rssi = inputData.getInt(KEY_RSSI, 0)
        val linkSpeedMbps = inputData.getInt(KEY_LINK_SPEED, 0)
        val wifiStandard = inputData.getInt(KEY_WIFI_STANDARD, 0)
        val frequencyMHz = inputData.getInt(KEY_FREQUENCY, 0)

        // It's good to check if any data is present before proceeding
        if (rssi == 0 && linkSpeedMbps == 0 && wifiStandard == 0 && frequencyMHz == 0) {
            Log.w(TAG, "No Wi-Fi data received to log. Exiting worker.")
            // Consider if this should be success if it means 'nothing to do' or failure if 'expected data not found'
            return Result.failure()
        }

        return try {
            val wifiLogEntry = WifiLogEntry(
                rssi = rssi,
                linkSpeedMbps = linkSpeedMbps,
                wifiStandard = wifiStandard,
                frequencyMHz = frequencyMHz
            )
            withContext(Dispatchers.IO) {
                wifiLogDao.insertWifiLog(wifiLogEntry)
            }
            Log.d(TAG, "Wi-Fi log saved: $wifiLogEntry")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Wi-Fi log: ${e.message}", e)
            Result.failure()
        }
    }
}