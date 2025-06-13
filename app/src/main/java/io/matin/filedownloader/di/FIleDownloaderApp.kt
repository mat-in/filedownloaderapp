package io.matin.filedownloader

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkerFactory
import dagger.hilt.android.HiltAndroidApp
import io.matin.filedownloader.di.ApplicationWorkerFactory // Import our custom factory
import javax.inject.Inject

@HiltAndroidApp
class FileDownloaderApp : Application(), Configuration.Provider {

    // Inject our custom WorkerFactory, which Hilt will provide
    @Inject
    lateinit var applicationWorkerFactory: ApplicationWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(applicationWorkerFactory) // Use our custom factory
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    // No need to manually call WorkManager.initialize in onCreate if using Configuration.Provider
}