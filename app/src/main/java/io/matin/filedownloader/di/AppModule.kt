package io.matin.filedownloader.di

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.matin.filedownloader.data.AppDatabase
import io.matin.filedownloader.data.DownloadDao
import io.matin.filedownloader.data.BatteryLogDao
import io.matin.filedownloader.data.WifiLogDao
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.notifications.DownloadNotificationManager
import io.matin.filedownloader.repo.FileDownloadRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .callTimeout(1000, TimeUnit.SECONDS)
            .connectTimeout(1000, TimeUnit.SECONDS)
            .readTimeout(1000, TimeUnit.SECONDS)
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().create()
    }
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }


    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFileDownloadRepository(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): FileDownloadRepository {
        return FileDownloadRepository(okHttpClient, gson)
    }

    @Provides
    @Singleton
    fun provideDownloadNotificationManager(@ApplicationContext context: Context): DownloadNotificationManager {
        return DownloadNotificationManager(context)
    }

    @Provides
    @Singleton
    fun provideFileStorageHelper(@ApplicationContext context: Context): FileStorageHelper {
        return FileStorageHelper(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(appDatabase: AppDatabase): DownloadDao {
        return appDatabase.downloadDao()
    }

    @Provides
    @Singleton
    fun provideBatteryLogDao(appDatabase: AppDatabase): BatteryLogDao {
        return appDatabase.batteryLogDao()
    }

    @Provides
    @Singleton
    fun provideWifiLogDao(appDatabase: AppDatabase): WifiLogDao {
        return appDatabase.wifiLogDao()
    }
}