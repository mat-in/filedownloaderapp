package io.matin.filedownloader.repo

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import com.google.gson.Gson
import io.matin.filedownloader.data.DownloadMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class FileDownloadRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {

    private val BASE_URL = "http://localhost:8080"

    /**
     * Fetches metadata for the next file to download from the backend.
     * Returns Result.failure if no more files are available or an error occurs.
     */
    suspend fun getNextFileMetadata(): Result<DownloadMetadata> {
        val url = "$BASE_URL/getNextFile"
        val request = Request.Builder()
            .url(url)
            .build()

        return try {

            val (response, responseBodyString) = withContext(Dispatchers.IO) {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                Pair(response, responseBody)
            }

            if (response.isSuccessful) {
                if (responseBodyString != null && responseBodyString.isNotBlank()) {
                    val metadata = gson.fromJson(responseBodyString, DownloadMetadata::class.java)
                    Log.d("FileDownloadRepo", "Successfully fetched metadata: $metadata")
                    Result.success(metadata)
                } else {
                    Log.d("FileDownloadRepo", "No more files available or empty metadata response.")
                    Result.failure(IOException("No more files available"))
                }
            } else if (response.code == 204) {
                Log.d("FileDownloadRepo", "Backend returned 204 No Content, indicating no more files.")
                Result.failure(IOException("No more files available (204 No Content)"))
            } else {
                val errorMessage = "Failed to get next file metadata: HTTP ${response.code} ${response.message}"
                Log.e("FileDownloadRepo", errorMessage)
                Result.failure(IOException(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("FileDownloadRepo", "Error fetching next file metadata: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a file from the backend's /getFile/{name} endpoint.
     * Supports range requests for resuming downloads.
     *
     * @param fileName The name of the file to download (used as a path parameter).
     * @param startByte The byte offset to start the download from (for resuming).
     * @return An OkHttp Response containing the file data.
     */
    suspend fun downloadFileFromBackend(fileName: String, startByte: Long = 0L): Response {
        val url = "$BASE_URL/getFile/${fileName.encodeURLPathSegment()}"
        val requestBuilder = Request.Builder().url(url)

        if (startByte > 0) {
            Log.d("FileDownloadRepo", "Resuming download for $fileName, setting Range header: bytes=$startByte-")
            requestBuilder.header("Range", "bytes=$startByte-")
        } else {
            Log.d("FileDownloadRepo", "Starting new download for $fileName.")
        }

        val request = requestBuilder.build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute()
        }
    }

    /**
     * Sends a success notification to the backend for a completed download.
     * Uses the /status/success/{name} endpoint.
     */
    suspend fun sendDownloadSuccess(fileName: String): Result<String> {
        val url = "$BASE_URL/status/success/${fileName.encodeURLPathSegment()}"
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val message = response.body?.string() ?: "Success message not provided"
                Log.d("FileDownloadRepo", "Download success reported for $fileName: $message")
                Result.success(message)
            } else {
                val errorMessage = "Failed to report download success for $fileName: HTTP ${response.code} ${response.message}"
                Log.e("FileDownloadRepo", errorMessage)
                Result.failure(IOException(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("FileDownloadRepo", "Error reporting download success for $fileName: ${e.message}", e)
            Result.failure(e)
        }
    }
}

fun String.encodeURLParameter(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")
}


fun String.encodeURLPathSegment(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")
        .replace("%2F", "/")
}
