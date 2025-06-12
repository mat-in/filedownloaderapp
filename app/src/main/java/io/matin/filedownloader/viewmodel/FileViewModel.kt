package io.matin.filedownloader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matin.filedownloader.filestorage.FileStorageHelper
import io.matin.filedownloader.network.ProgressResponseBody
import io.matin.filedownloader.repo.FileDownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    private val fileDownloadRepository: FileDownloadRepository,
    private val fileStorageHelper: FileStorageHelper
) : ViewModel() {

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        data class Progress(val percentage: Int) : DownloadStatus()
        object Completed : DownloadStatus()
        data class Failed(val message: String?) : DownloadStatus()
        object Loading : DownloadStatus()
    }

    fun startDownload(url: String, fileName: String) {
        _downloadStatus.value = DownloadStatus.Loading

        viewModelScope.launch {
            try {
                val progressListener = object : ProgressResponseBody.ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        val progress = if (contentLength > 0) ((bytesRead.toFloat() / contentLength) * 100).toInt() else 0
                        _downloadStatus.value = DownloadStatus.Progress(progress)
                    }
                }

                val responseBody = fileDownloadRepository.downloadFile(url, progressListener)
                val savedUri = fileStorageHelper.saveFileToMediaStore(responseBody, fileName)

                if (savedUri != null) {
                    _downloadStatus.value = DownloadStatus.Completed
                } else {
                    _downloadStatus.value = DownloadStatus.Failed("Failed to save file.")
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown download error"
                _downloadStatus.value = DownloadStatus.Failed(errorMessage)
            }
        }
    }

    fun resetDownloadStatus() {
        _downloadStatus.value = DownloadStatus.Idle
    }
}
