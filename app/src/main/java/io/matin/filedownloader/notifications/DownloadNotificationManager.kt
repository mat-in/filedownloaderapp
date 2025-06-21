package io.matin.filedownloader.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresPermission
import android.Manifest
import android.util.Log // Import Log
import io.matin.filedownloader.MainActivity
import io.matin.filedownloader.R // Keep R import if you use other resources

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotificationManager @Inject constructor(
    private val context: Context
) {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "file_download_channel_workmanager"
        const val DOWNLOAD_CHANNEL_NAME = "File Downloads (Background)"
        const val DOWNLOAD_NOTIFICATION_ID = 1001
        private const val TAG = "DownloadNotifManager"
    }

    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                DOWNLOAD_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background file download progress and status."
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $DOWNLOAD_CHANNEL_ID")
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun buildInitialProgressNotification(fileName: String, progress: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading: $fileName")
            .setContentText("$progress% complete")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
        Log.d(TAG, "Built initial progress notification for $fileName at $progress%")
        return notification
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showProgressNotification(fileName: String, progress: Int) {
        val notification = buildInitialProgressNotification(fileName, progress)
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
        Log.d(TAG, "Showing progress notification for $fileName at $progress%")
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showDownloadComplete(fileName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete: $fileName")
            .setContentText("File saved successfully.")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Showing download complete notification for $fileName")
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showDownloadFailed(fileName: String, errorMessage: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Download Failed: $fileName")
            .setContentText("Error: ${errorMessage ?: "Unknown"}")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Showing download failed notification for $fileName")
    }

    fun cancelNotification() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
        Log.d(TAG, "Cancelled notification with ID: $DOWNLOAD_NOTIFICATION_ID")
    }
}