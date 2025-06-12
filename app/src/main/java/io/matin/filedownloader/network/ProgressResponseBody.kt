package io.matin.filedownloader.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener
) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, done: Boolean)
    }

    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        if (bufferedSource == null) {
            bufferedSource = source(responseBody.source()).buffer()
        }
        return bufferedSource!!
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            var lastReportedProgress = -1

            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                val currentBytesRead = if (bytesRead != -1L) totalBytesRead + bytesRead else totalBytesRead
                totalBytesRead = currentBytesRead

                val contentLength = responseBody.contentLength()

                if (contentLength > 0) {
                    val currentProgress = ((totalBytesRead.toFloat() / contentLength) * 100).toInt()
                    if (currentProgress != lastReportedProgress || bytesRead == -1L) {
                        lastReportedProgress = currentProgress
                        progressListener.update(totalBytesRead, contentLength, bytesRead == -1L)
                    }
                } else if (bytesRead == -1L) {
                    progressListener.update(totalBytesRead, contentLength, true)
                }
                return bytesRead
            }
        }
    }
}