package io.matin.filedownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var urlEditText: EditText
    private lateinit var downloadButton: Button
    private lateinit var statusTextView: TextView

    private val fileViewModel: FileViewModel by viewModels()
    private lateinit var notificationManager: NotificationManagerCompat

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkStoragePermissionAndInitiateDownload()
        } else {
            statusTextView.text = "Notification permission denied. Cannot show progress."
            Log.w("MainActivity", "Notification permission denied by user.")
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

        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        urlEditText.setText("https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4")

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
            withContext(Dispatchers.Main) {
                fileViewModel.downloadStatus.collect { status ->
                    when (status) {
                        FileViewModel.DownloadStatus.Idle -> {
                            statusTextView.text = "Status: Idle"
                            cancelNotification()
                        }
                        FileViewModel.DownloadStatus.Loading -> {
                            statusTextView.text = "Status: Initializing download..."
                        }
                        is FileViewModel.DownloadStatus.Progress -> {
                            statusTextView.text = "Downloading: ${status.percentage}%"
                            // Ensure permission for notification update
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    showProgressNotification(getFileNameFromUrl(urlEditText.text.toString()), status.percentage)
                                }
                            } else {
                                showProgressNotification(getFileNameFromUrl(urlEditText.text.toString()), status.percentage)
                            }
                        }
                        FileViewModel.DownloadStatus.Completed -> {
                            statusTextView.text = "Download successful!"
                            // Ensure permission for notification
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    showDownloadComplete(getFileNameFromUrl(urlEditText.text.toString()))
                                }
                            } else {
                                showDownloadComplete(getFileNameFromUrl(urlEditText.text.toString()))
                            }
                        }
                        is FileViewModel.DownloadStatus.Failed -> {
                            val fileName = getFileNameFromUrl(urlEditText.text.toString())
                            val errorMessage = status.message ?: "Unknown error"
                            statusTextView.text = "Download failed: $errorMessage"
                            Log.e("MainActivity", "Download failed: $errorMessage")
                            // Ensure permission for notification
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    showDownloadFailed(fileName, errorMessage)
                                }
                            } else {
                                showDownloadFailed(fileName, errorMessage)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkStoragePermissionAndInitiateDownload() {
        initiateDownload()
    }

    private fun initiateDownload() {
        val urlString = urlEditText.text.toString().trim()
        if (urlString.isEmpty()) {
            statusTextView.text = "Please enter a URL."
            return
        }

        val fileName = getFileNameFromUrl(urlString)
        // Show initial 0% notification ONLY if permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                showProgressNotification(fileName, 0)
            }
        } else {
            showProgressNotification(fileName, 0)
        }

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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for ongoing and completed file downloads."
            setSound(null, null)
            enableVibration(false)
        }
        // Use notificationManager directly to create the channel
        notificationManager.createNotificationChannel(channel)
    }

    // @RequiresPermission is added directly to the methods as they require the permission
    // The actual permission check is done before calling these methods.
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showProgressNotification(fileName: String, progress: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading: $fileName")
            .setContentText("$progress% complete")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showDownloadComplete(fileName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Complete: $fileName")
            .setContentText("File saved successfully.")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showDownloadFailed(fileName: String, errorMessage: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Failed: $fileName")
            .setContentText("Error: ${errorMessage ?: "Unknown"}")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }

    private fun cancelNotification() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "file_download_channel"
        const val CHANNEL_NAME = "File Downloads"
        const val DOWNLOAD_NOTIFICATION_ID = 1001
    }
}