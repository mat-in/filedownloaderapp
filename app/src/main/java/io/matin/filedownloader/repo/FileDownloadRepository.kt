// package io.matin.filedownloader.repo

package io.matin.filedownloader.repo

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileDownloadRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    @Throws(IOException::class)
    fun downloadFile(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .build()
        return okHttpClient.newCall(request).execute()
    }
}