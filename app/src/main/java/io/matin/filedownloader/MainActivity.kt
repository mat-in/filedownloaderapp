package io.matin.filedownloader

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.matin.filedownloader.viewmodel.FileViewModel
import kotlinx.coroutines.launch
import io.matin.filedownloader.utils.StorageUtils
import io.matin.filedownloader.receivers.BatteryInfoReceiver
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BatteryInfoReceiver.BatteryUpdateListener {

    private lateinit var downloadButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var totalStorageTextView: TextView
    private lateinit var freeSpaceTextView: TextView
    private lateinit var batteryPercentageTextView: TextView
    private lateinit var chargingStatusTextView: TextView
    private lateinit var powerConsumptionTextView: TextView

    private lateinit var urlEditText: EditText

    private val fileViewModel: FileViewModel by viewModels()

    private lateinit var batteryInfoReceiver: BatteryInfoReceiver

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            lifecycleScope.launch { initiateDownloadProcess() }
        } else {
            statusTextView.text = "Storage permission denied. Cannot save file."
            Log.w("MainActivity", "Storage permission denied by user.")
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkStoragePermissionAndInitiateDownloadProcess()
        } else {
            statusTextView.text = "Notification permission denied. Download will proceed without notification."
            Log.w("MainActivity", "Notification permission denied by user, notifications will not be shown.")
            checkStoragePermissionAndInitiateDownloadProcess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        downloadButton = findViewById(R.id.downloadButton)
        statusTextView = findViewById(R.id.statusTextView)
        totalStorageTextView = findViewById(R.id.totalStorageTextView)
        freeSpaceTextView = findViewById(R.id.freeSpaceTextView)
        batteryPercentageTextView = findViewById(R.id.batteryPercentageTextView)
        chargingStatusTextView = findViewById(R.id.chargingStatusTextView)
        powerConsumptionTextView = findViewById(R.id.powerConsumptionTextView)

        urlEditText = findViewById(R.id.urlEditText)

        downloadButton.setOnClickListener {
            // UI elements are controlled by observing downloadStatus
            if (fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.Idle ||
                fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.Failed ||
                fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.AllDownloadsCompleted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        checkStoragePermissionAndInitiateDownloadProcess()
                    } else {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    checkStoragePermissionAndInitiateDownloadProcess()
                }
            } else {
                Toast.makeText(this, "Download already in progress or enqueued.", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileViewModel.downloadStatus.collect { status ->
                    Log.d("MainActivity", "DownloadStatus observed: $status")
                    when (status) {
                        FileViewModel.DownloadStatus.Idle -> {
                            statusTextView.text = "Status: Ready to fetch file metadata."
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                            powerConsumptionTextView.text = "Power Consumption: N/A"
                        }
                        is FileViewModel.DownloadStatus.Enqueued -> {
                            statusTextView.text = "Download enqueued (Work ID: ${status.workId})"
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                            powerConsumptionTextView.text = "Power Consumption: Measuring..."
                        }
                        FileViewModel.DownloadStatus.Loading -> {
                            statusTextView.text = "Status: Initializing download..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                            powerConsumptionTextView.text = "Power Consumption: Measuring..."
                        }
                        is FileViewModel.DownloadStatus.Progress -> {
                            statusTextView.text = "Downloading: ${status.percentage}%"
                            downloadButton.isEnabled = false
                        }
                        is FileViewModel.DownloadStatus.Completed -> {
                            val consumptionText = status.powerConsumptionAmps?.let {
                                "%.4f Amps".format(it)
                            } ?: "N/A (Could not measure)"
                            statusTextView.text = "Download successful! Checking for next file..."
                            powerConsumptionTextView.text = "Last File Power Consumption: $consumptionText"
                            updateStorageInfo()
                        }
                        is FileViewModel.DownloadStatus.Failed -> {
                            statusTextView.text = "Download failed: ${status.message ?: "Unknown error"}"
                            Log.e("MainActivity", "Download failed: ${status.message ?: "Unknown error"}")
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                            powerConsumptionTextView.text = "Power Consumption: N/A (Failed)"
                        }
                        FileViewModel.DownloadStatus.FetchingMetadata -> {
                            statusTextView.text = "Status: Fetching file metadata from backend..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                            powerConsumptionTextView.text = "Power Consumption: N/A"
                        }
                        is FileViewModel.DownloadStatus.MetadataFetched -> {
                            statusTextView.text = "Status: Metadata fetched for ${status.fileName}. Enqueuing download..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                            powerConsumptionTextView.text = "Power Consumption: N/A"
                        }
                        FileViewModel.DownloadStatus.AllDownloadsCompleted -> {
                            statusTextView.text = "All files downloaded successfully!"
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                            updateStorageInfo()
                            powerConsumptionTextView.text = "Power Consumption: All downloads completed."
                        }
                    }
                }
            }
        }

        updateStorageInfo()

        batteryInfoReceiver = BatteryInfoReceiver(this)
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryInfoReceiver, iFilter)

        val batteryStatus: Intent? = registerReceiver(null, iFilter)
        updateBatteryInfo(batteryStatus)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryInfoReceiver)
    }

    private fun checkStoragePermissionAndInitiateDownloadProcess() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                lifecycleScope.launch { initiateDownloadProcess() }
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            lifecycleScope.launch { initiateDownloadProcess() }
        }
    }

    private suspend fun initiateDownloadProcess() {
        val currentUrl = urlEditText.text.toString().trim()
        if (currentUrl.isNotBlank()) {
            fileViewModel.setBaseUrl(currentUrl)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Starting download with URL: $currentUrl", Toast.LENGTH_SHORT).show()
            }
            fileViewModel.startBackendDrivenDownload()
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Please enter a valid backend URL.", Toast.LENGTH_LONG).show()
                urlEditText.isEnabled = true
            }
            fileViewModel.resetDownloadStatus()
        }
    }

    private fun updateStorageInfo() {
        val (totalInternal, freeInternal) = StorageUtils.getInternalStorageInfo()
        totalStorageTextView.text = "Total Internal Storage: ${StorageUtils.formatFileSize(totalInternal)}"
        freeSpaceTextView.text = "Free Internal Space: ${StorageUtils.formatFileSize(freeInternal)}"
    }

    override fun onBatteryInfoUpdated(percentage: Int, isCharging: Boolean) {
        batteryPercentageTextView.text = "Battery: $percentage%"
        chargingStatusTextView.text = "Charging: ${if (isCharging) "Yes" else "No"}"
    }

    private fun updateBatteryInfo(batteryStatus: Intent?) {
        if (batteryStatus == null) return

        val level: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct: Int = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

        val status: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        onBatteryInfoUpdated(batteryPct, isCharging)
    }
}