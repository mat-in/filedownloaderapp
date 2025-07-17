package io.matin.filedownloader

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.matin.filedownloader.receivers.BatteryInfoReceiver
import io.matin.filedownloader.receivers.WifiScanReceiver
import io.matin.filedownloader.ui.BatteryInfoFragment
import io.matin.filedownloader.ui.MainFragment
import io.matin.filedownloader.ui.WifiInfoFragment
import io.matin.filedownloader.viewmodel.FileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BatteryInfoReceiver.BatteryUpdateListener, MainFragment.MainFragmentListener {

    private val fileViewModel: FileViewModel by viewModels()

    private lateinit var batteryInfoReceiver: BatteryInfoReceiver
    private lateinit var wifiScanReceiver: WifiScanReceiver

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
        if (isGranted) {
            val url = mainFragment?.getEnteredUrl() ?: ""
            if (url.isNotBlank()) {
                lifecycleScope.launch { initiateDownloadProcess(url) }
            } else {
                mainFragment?.showToast("Please enter a valid backend URL.")
                Log.w("MainActivity", "URL is blank after storage permission granted.")
            }
        } else {
            mainFragment?.showToast("Storage permission denied. Cannot save file.")
            Log.w("MainActivity", "Storage permission denied by user.")
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
        if (!isGranted) {
            mainFragment?.showToast("Notification permission denied. Download will proceed without notification.")
            Log.w("MainActivity", "Notification permission denied by user, notifications will not be shown.")
        }
        checkStoragePermissionAndInitiateDownloadProcess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setTitle("File Downloader") // Set initial title

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Load MainFragment initially if no fragment is already present (e.g., after rotation)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment(), "MainFragmentTag")
                .commit()
        }

        // Persistent listener for back stack changes to update toolbar
        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarBasedOnFragment()
        }

        batteryInfoReceiver = BatteryInfoReceiver(this)
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryInfoReceiver, batteryFilter)

        wifiScanReceiver = WifiScanReceiver()
        val wifiFilter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        registerReceiver(wifiScanReceiver, wifiFilter)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (item.itemId) {
            R.id.action_battery_info -> {
                if (currentFragment !is BatteryInfoFragment) {
                    showFragment(BatteryInfoFragment(), "Battery Information")
                }
                return true
            }
            R.id.action_wifi_info -> {
                if (currentFragment !is WifiInfoFragment) {
                    showFragment(WifiInfoFragment(), "Wi-Fi Information")
                }
                return true
            }
            android.R.id.home -> { // Handle the Up button (back button in toolbar)
                onBackPressedDispatcher.onBackPressed() // Use onBackPressedDispatcher for modern Android
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    // Helper function to show a fragment
    private fun showFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // Allows going back to previous fragment/MainFragment
            .commit()
        // Toolbar update will be handled by the onBackStackChangedListener
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryInfoReceiver)
        unregisterReceiver(wifiScanReceiver)
    }

    // --- MainFragmentListener implementations ---

    override fun onDownloadInitiated(url: String) {
        lifecycleScope.launch { initiateDownloadProcess(url) }
    }

    override fun onStoragePermissionNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
                val url = mainFragment?.getEnteredUrl() ?: ""
                lifecycleScope.launch { initiateDownloadProcess(url) }
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
            val url = mainFragment?.getEnteredUrl() ?: ""
            lifecycleScope.launch { initiateDownloadProcess(url) }
        }
    }

    override fun onNotificationPermissionNeeded() {
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
    }

    private fun checkStoragePermissionAndInitiateDownloadProcess() {
        val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
        val currentUrl = mainFragment?.getEnteredUrl() ?: ""

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch { initiateDownloadProcess(currentUrl) }
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            lifecycleScope.launch { initiateDownloadProcess(currentUrl) }
        }
    }

    private suspend fun initiateDownloadProcess(currentUrl: String) {
        val mainFragment = supportFragmentManager.findFragmentByTag("MainFragmentTag") as? MainFragment
        if (currentUrl.isNotBlank()) {
            fileViewModel.setBaseUrl(currentUrl)
            withContext(Dispatchers.Main) {
                mainFragment?.showToast("Starting download with URL: $currentUrl")
            }
            fileViewModel.startBackendDrivenDownload()
        } else {
            withContext(Dispatchers.Main) {
                mainFragment?.showToast("Please enter a valid backend URL.")
            }
            fileViewModel.resetDownloadStatus()
        }
    }

    override fun onBatteryInfoUpdated(percentage: Int, isCharging: Boolean) {
        Log.d("MainActivity", "Battery update received (via receiver): $percentage%, Charging: $isCharging")
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed() // Let system handle (exit app) if no fragments on stack
        }
    }

    // New method to update toolbar based on current fragment
    private fun updateToolbarBasedOnFragment() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (currentFragment) {
            is MainFragment -> {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.title = "File Downloader"
            }
            is BatteryInfoFragment -> {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = "Battery Information"
            }
            is WifiInfoFragment -> {
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                supportActionBar?.title = "Wi-Fi Information"
            }
            else -> {
                // Fallback for unexpected states, e.g., if no fragment is attached
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.title = "File Downloader"
            }
        }
    }
}