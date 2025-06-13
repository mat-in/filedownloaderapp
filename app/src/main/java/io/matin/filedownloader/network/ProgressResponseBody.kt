package io.matin.filedownloader.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import android.util.Log

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener
) : ResponseBody() {

    private var bufferedSource: BufferedSource? = null

    interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, done: Boolean, percentage: Int)
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

            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                val currentBytesRead = if (bytesRead != -1L) totalBytesRead + bytesRead else totalBytesRead
                totalBytesRead = currentBytesRead

                val contentLength = responseBody.contentLength()

                val currentPercentage = if (contentLength > 0) {
                    ((totalBytesRead.toFloat() / contentLength) * 100).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                Log.d("ProgressResponseBody", "read: totalBytesRead=$totalBytesRead, contentLength=$contentLength, currentPercentage=$currentPercentage, bytesRead=$bytesRead")

                if (bytesRead != -1L) {
                    progressListener.update(totalBytesRead, contentLength, false, currentPercentage)
                } else {
                    progressListener.update(totalBytesRead, contentLength, true, 100)
                }

                return bytesRead
            }
        }
    }
}