// io.matin.filedownloader.utils.ErrorLogCollector.kt
package io.matin.filedownloader.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object ErrorLogCollector {
    private val _logMessages = MutableLiveData<String>()
    val logMessages: LiveData<String> = _logMessages

    private val logQueue = ConcurrentLinkedQueue<String>()
    private const val MAX_LOG_LINES = 50 // Keep this reasonable
    private val dateFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormatter.format(Date())
        var logEntry = "$timestamp E/$tag: $message"
        if (throwable != null) {
            val stackTrace = Log.getStackTraceString(throwable)
            logEntry += "\n  " + stackTrace.take(200) // Limit stack trace length for display
        }

        // Add to queue and maintain max size
        logQueue.add(logEntry)
        while (logQueue.size > MAX_LOG_LINES) {
            logQueue.poll() // Remove oldest entry
        }

        // Update LiveData on the main thread
        _logMessages.postValue(logQueue.joinToString("\n"))

        // Also log to actual Logcat for development
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}