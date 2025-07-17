package io.matin.filedownloader.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.matin.filedownloader.data.BatteryLogDao
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.notifications.DownloadNotificationManager
import io.matin.filedownloader.repo.FileDownloadRepository
import io.matin.filedownloader.workers.DownloadWorker
import javax.inject.Inject
import javax.inject.Singleton
import io.matin.filedownloader.data.DownloadDao


@Singleton
class ApplicationWorkerFactory @Inject constructor(
    private val fileDownloadRepository: FileDownloadRepository,
    private val notificationManager: DownloadNotificationManager,
    private val fileStorageHelper: FileStorageHelper,
    private val downloadDao: DownloadDao,
    private val batteryLogDao: BatteryLogDao
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            DownloadWorker::class.java.name -> {
                DownloadWorker(
                    appContext,
                    workerParameters,
                    fileDownloadRepository,
                    notificationManager,
                    fileStorageHelper,
                    downloadDao,
                    batteryLogDao
                )
            }
            else -> null
        }
    }
}