package io.matin.filedownloader.repo

import io.matin.filedownloader.network.DownloadService
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class FileDownloadRepository @Inject constructor(
    private val downloadService: DownloadService,
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadFile(url: String): ResponseBody {
        return downloadService.downloadFile(url)
    }
}