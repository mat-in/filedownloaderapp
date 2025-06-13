package io.matin.filedownloader.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.notifications.DownloadNotificationManager
import io.matin.filedownloader.repo.FileDownloadRepository
import io.matin.filedownloader.workers.DownloadWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplicationWorkerFactory @Inject constructor(
    private val fileDownloadRepository: FileDownloadRepository,
    private val notificationManager: DownloadNotificationManager,
    private val fileStorageHelper: FileStorageHelper
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return if (workerClassName == DownloadWorker::class.java.name) {
            DownloadWorker(
                appContext,
                workerParameters,
                fileDownloadRepository,
                notificationManager,
                fileStorageHelper
            )
        } else {
            // For workers not handled by this factory, return null.
            // WorkManager will then try other registered factories or its default factory.
            return null
        }
    }
}