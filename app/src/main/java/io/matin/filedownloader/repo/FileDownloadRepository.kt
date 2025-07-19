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
import java.util.concurrent.atomic.AtomicReference
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.NoRouteToHostException

@Singleton
class FileDownloadRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    // Use AtomicReference for thread-safe updates to the base URL
    // Initialize with a default or empty string; it MUST be set before any network calls.
    private val _baseUrl = AtomicReference<String>("")

    // Setter function to update the base URL
    fun setBaseUrl(url: String) {
        _baseUrl.set(url)
        Log.d("FileDownloadRepo", "Base URL set to: ${url}")
    }

    private fun getBaseUrl(): String {
        val url = _baseUrl.get()
        if (url.isNullOrBlank()) {
            throw IllegalStateException("Base URL is not set in FileDownloadRepository. Call setBaseUrl() first.")
        }
        return url
    }


    /**
     * Fetches metadata for the next file to download from the backend.
     * Returns Result.failure if no more files are available or an error occurs.
     */
    suspend fun getNextFileMetadata(): Result<DownloadMetadata> {
        val url = "${getBaseUrl()}/getNextFile" // Use the dynamic base URL
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
            val specificErrorMessage = when (e) {
                is ConnectException -> "Connection failed: Check server address or network connection."
                is UnknownHostException -> "Unknown host: Check server address or DNS settings."
                is NoRouteToHostException -> "No route to host: Server unreachable."
                is IOException -> "Network error during metadata fetch: ${e.message}"
                else -> "An unexpected error occurred during metadata fetch: ${e.message}"
            }
            Log.e("FileDownloadRepo", specificErrorMessage, e)
            Result.failure(IOException(specificErrorMessage, e))
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
        val url = "${getBaseUrl()}/getFile/${fileName.encodeURLPathSegment()}" // Use dynamic base URL
        val requestBuilder = Request.Builder().url(url)

        if (startByte > 0) {
            Log.d("FileDownloadRepo", "Resuming download for $fileName, setting Range header: bytes=$startByte-")
            requestBuilder.header("Range", "bytes=$startByte-")
        } else {
            Log.d("FileDownloadRepo", "Starting new download for $fileName.")
        }

        val request = requestBuilder.build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                val specificErrorMessage = when (e) {
                    is ConnectException -> "Connection failed for file download: Check server address or network connection."
                    is UnknownHostException -> "Unknown host for file download: Check server address or DNS settings."
                    is NoRouteToHostException -> "No route to host for file download: Server unreachable."
                    is IOException -> "Network error during file download: ${e.message}"
                    else -> "An unexpected error occurred during file download: ${e.message}"
                }
                Log.e("FileDownloadRepo", specificErrorMessage, e)
                throw IOException(specificErrorMessage, e)
            }
        }
    }

    /**
     * Sends a success notification to the backend for a completed download.
     * Uses the /status/success/{name} endpoint.
     */
    suspend fun sendDownloadSuccess(fileName: String): Result<String> {
        val url = "${getBaseUrl()}/status/success/${fileName.encodeURLPathSegment()}" // Use dynamic base URL
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
                val message = responseBodyString ?: "Success message not provided"
                Log.d("FileDownloadRepo", "Download success reported for $fileName: $message")
                Result.success(message)
            } else {
                val errorMessage = "Failed to report download success for $fileName: HTTP ${response.code} ${response.message}"
                Log.e("FileDownloadRepo", errorMessage)
                Result.failure(IOException(errorMessage))
            }
        } catch (e: Exception) {
            val specificErrorMessage = when (e) {
                is ConnectException -> "Connection failed for success report: Check server address or network connection."
                is UnknownHostException -> "Unknown host for success report: Check server address or DNS settings."
                is NoRouteToHostException -> "No route to host for success report: Server unreachable."
                is IOException -> "Network error during success report: ${e.message}"
                else -> "An unexpected error occurred during success report: ${e.message}"
            }
            Log.e("FileDownloadRepo", specificErrorMessage, e)
            Result.failure(IOException(specificErrorMessage, e))
        }
    }
}

// Helper extension function for URL encoding query parameters
fun String.encodeURLParameter(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20") // Replace + with %20 for space encoding
}

// Helper extension function for URL encoding path segments
fun String.encodeURLPathSegment(): String {
    return java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")
        .replace("%2F", "/") // Don't encode forward slashes if they are part of the path structure
}