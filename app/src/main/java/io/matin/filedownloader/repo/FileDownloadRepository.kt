package io.matin.filedownloader.repo

import io.matin.filedownloader.network.DownloadService
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class FileDownloadRepository @Inject constructor(
    private val downloadService: DownloadService,
    private val okHttpClient: OkHttpClient // Keep okHttpClient if other network operations need it directly
) {
    // This method now directly calls the download service and returns the raw ResponseBody.
    // Progress tracking will be handled by ProgressResponseBody wrapping this ResponseBody
    // within the DownloadWorker.
    suspend fun downloadFile(url: String): ResponseBody {
        // The OkHttpClient injected into this repository is implicitly used by Retrofit
        // if it's the one passed during Retrofit.Builder().client(okHttpClient).
        // No explicit interceptor handling is needed here; it's in AppModule.
        return downloadService.downloadFile(url)
    }
}