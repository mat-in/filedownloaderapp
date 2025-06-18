package io.matin.filedownloader.di

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.matin.filedownloader.network.DownloadService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.matin.filedownloader.data.AppDatabase
import io.matin.filedownloader.data.DownloadDao
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.notifications.DownloadNotificationManager
import io.matin.filedownloader.repo.FileDownloadRepository

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun providesBaseUrl(): String {
        return "http://localhost/"
    }

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
    fun providesRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
    }

    @Provides
    @Singleton
    fun providesDownloadService(retrofit: Retrofit): DownloadService {
        return retrofit.create(DownloadService::class.java)
    }

    // Provide Application Context - needed by DownloadNotificationManager and FileStorageHelper
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    // Provide WorkManager instance - needed by FileViewModel
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFileDownloadRepository(
        downloadService: DownloadService,
        okHttpClient: OkHttpClient
    ): FileDownloadRepository {
        return FileDownloadRepository(downloadService, okHttpClient)
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
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(appDatabase: AppDatabase): DownloadDao {
        return appDatabase.downloadDao()
    }
}