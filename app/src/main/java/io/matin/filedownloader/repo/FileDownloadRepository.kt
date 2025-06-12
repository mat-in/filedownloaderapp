package io.matin.filedownloader.repo

import io.matin.filedownloader.network.DownloadService
import io.matin.filedownloader.network.ProgressResponseBody
import okhttp3.Request
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileDownloadRepository @Inject constructor(
    private val downloadService: DownloadService
) {
    suspend fun downloadFile(url: String, progressListener: ProgressResponseBody.ProgressListener): ResponseBody {
        val request = Request.Builder()
            .url(url)
            .tag(ProgressResponseBody.ProgressListener::class.java, progressListener)
            .build()
        return downloadService.downloadFile(request.url.toString())
    }
}