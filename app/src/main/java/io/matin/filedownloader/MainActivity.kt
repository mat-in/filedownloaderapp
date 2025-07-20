// io.matin.filedownloader.MainActivity.kt
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
class MainActivity : AppCompatActivity(), MainFragment.MainFragmentListener {

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
        supportActionBar?.setTitle("File Downloader")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment(), "MainFragmentTag")
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarBasedOnFragment()
        }

        // Initialize and register BatteryInfoReceiver
        batteryInfoReceiver = BatteryInfoReceiver()
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryInfoReceiver, batteryFilter)
        Log.d("MainActivity", "BatteryInfoReceiver registered.")


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
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryInfoReceiver)
        Log.d("MainActivity", "BatteryInfoReceiver unregistered.")
        unregisterReceiver(wifiScanReceiver)
    }


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

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }

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
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                supportActionBar?.title = "File Downloader"
            }
        }
    }
}