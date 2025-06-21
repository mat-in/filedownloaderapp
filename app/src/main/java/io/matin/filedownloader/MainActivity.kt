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
    import java.net.URL
    import io.matin.filedownloader.utils.StorageUtils
    import io.matin.filedownloader.receivers.BatteryInfoReceiver

    @AndroidEntryPoint
    class MainActivity : AppCompatActivity(), BatteryInfoReceiver.BatteryUpdateListener {

        private lateinit var urlEditText: EditText
        private lateinit var downloadButton: Button
        private lateinit var statusTextView: TextView
        private lateinit var totalStorageTextView: TextView
        private lateinit var freeSpaceTextView: TextView
        private lateinit var batteryPercentageTextView: TextView
        private lateinit var chargingStatusTextView: TextView

        private val fileViewModel: FileViewModel by viewModels()

        private lateinit var batteryInfoReceiver: BatteryInfoReceiver

        private val requestStoragePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                initiateDownload()
            } else {
                statusTextView.text = "Storage permission denied. Cannot save file."
                Log.w("MainActivity", "Storage permission denied by user.")
            }
        }

        private val requestNotificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                checkStoragePermissionAndInitiateDownload()
            } else {
                statusTextView.text = "Notification permission denied. Download will proceed without notification."
                Log.w("MainActivity", "Notification permission denied by user, notifications will not be shown.")
                checkStoragePermissionAndInitiateDownload()
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

            urlEditText = findViewById(R.id.urlEditText)
            downloadButton = findViewById(R.id.downloadButton)
            statusTextView = findViewById(R.id.statusTextView)
            totalStorageTextView = findViewById(R.id.totalStorageTextView)
            freeSpaceTextView = findViewById(R.id.freeSpaceTextView)
            batteryPercentageTextView = findViewById(R.id.batteryPercentageTextView)
            chargingStatusTextView = findViewById(R.id.chargingStatusTextView)

            urlEditText.setText("https://archive.org/download/pdfsandebooks/UP/Heal%20Yourself%20with%20Yoga.pdf")

            downloadButton.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        checkStoragePermissionAndInitiateDownload()
                    } else {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    checkStoragePermissionAndInitiateDownload()
                }
            }

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    fileViewModel.downloadStatus.collect { status ->
                        Log.d("MainActivity", "DownloadStatus observed: $status")
                        when (status) {
                            FileViewModel.DownloadStatus.Idle -> {
                                statusTextView.text = "Status: Idle"
                            }
                            is FileViewModel.DownloadStatus.Enqueued -> {
                                statusTextView.text = "Download enqueued (Work ID: ${status.workId})"
                            }
                            FileViewModel.DownloadStatus.Loading -> {
                                statusTextView.text = "Status: Initializing download..."
                            }
                            is FileViewModel.DownloadStatus.Progress -> {
                                statusTextView.text = "Downloading: ${status.percentage}%"
                            }
                            FileViewModel.DownloadStatus.Completed -> {
                                statusTextView.text = "Download successful!"
                                updateStorageInfo()
                            }
                            is FileViewModel.DownloadStatus.Failed -> {
                                statusTextView.text = "Download failed: ${status.message ?: "Unknown error"}"
                                Log.e("MainActivity", "Download failed: ${status.message ?: "Unknown error"}")
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

        private fun checkStoragePermissionAndInitiateDownload() {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    initiateDownload()
                } else {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else {
                initiateDownload()
            }
        }

        private fun initiateDownload() {
            val urlString = urlEditText.text.toString().trim()
            if (urlString.isEmpty()) {
                statusTextView.text = "Please enter a URL."
                return
            }

            val fileName = getFileNameFromUrl(urlString)
            fileViewModel.startDownload(urlString, fileName)
        }

        private fun getFileNameFromUrl(url: String): String {
            return try {
                val urlObject = URL(url)
                val path = urlObject.path
                if (path.isNotEmpty() && path != "/") {
                    path.substringAfterLast('/')
                } else {
                    "downloaded_file_${System.currentTimeMillis()}.bin"
                }
            } catch (e: Exception) {
                "downloaded_file_${System.currentTimeMillis()}.bin"
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