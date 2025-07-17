package io.matin.filedownloader.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.matin.filedownloader.workers.WifiLoggingWorker

class WifiScanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiScanReceiver"
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == WifiManager.RSSI_CHANGED_ACTION || intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo: WifiInfo? = wifiManager.connectionInfo

            wifiInfo?.let { info ->
                val rssi = info.rssi
                val linkSpeedMbps = info.linkSpeed
                val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.wifiStandard else 0 // WifiStandard added in API 29
                val frequencyMHz = info.frequency

                Log.d(TAG, "Wi-Fi Info Received: RSSI=$rssi, LinkSpeed=${linkSpeedMbps}Mbps, Standard=$standard, Freq=${frequencyMHz}MHz")

                // Enqueue a WorkManager job to save this data
                val workData = workDataOf(
                    WifiLoggingWorker.KEY_RSSI to rssi,
                    WifiLoggingWorker.KEY_LINK_SPEED to linkSpeedMbps,
                    WifiLoggingWorker.KEY_WIFI_STANDARD to standard,
                    WifiLoggingWorker.KEY_FREQUENCY to frequencyMHz
                )

                val wifiLogRequest = OneTimeWorkRequestBuilder<WifiLoggingWorker>()
                    .setInputData(workData)
                    .build()

                WorkManager.getInstance(context).enqueue(wifiLogRequest)
            } ?: run {
                Log.d(TAG, "No active Wi-Fi connection info available.")
            }
        }
    }
}