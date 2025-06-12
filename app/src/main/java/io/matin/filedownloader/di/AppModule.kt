package io.matin.filedownloader.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.matin.filedownloader.network.DownloadService
import io.matin.filedownloader.network.ProgressResponseBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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

        builder.addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)

            val progressListener = originalRequest.tag(ProgressResponseBody.ProgressListener::class.java)

            if (progressListener != null && response.body != null) {
                response.newBuilder()
                    .body(ProgressResponseBody(response.body!!, progressListener))
                    .build()
            } else {
                response
            }
        }
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
}