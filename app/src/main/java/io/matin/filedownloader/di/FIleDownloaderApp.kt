package io.matin.filedownloader

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkerFactory
import dagger.hilt.android.HiltAndroidApp
import io.matin.filedownloader.di.ApplicationWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class FileDownloaderApp : Application(), Configuration.Provider {


    @Inject
    lateinit var applicationWorkerFactory: ApplicationWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(applicationWorkerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}