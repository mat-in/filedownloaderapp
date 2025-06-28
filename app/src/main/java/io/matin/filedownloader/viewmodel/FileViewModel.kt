package io.matin.filedownloader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matin.filedownloader.workers.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.util.Log
import androidx.lifecycle.asFlow
import io.matin.filedownloader.repo.FileDownloadRepository
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class FileViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val fileDownloadRepository: FileDownloadRepository
) : ViewModel() {

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val _isDownloadingQueueActive = MutableStateFlow(false)

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        object Loading : DownloadStatus()
        data class Progress(val percentage: Int) : DownloadStatus()
        data class Completed(val powerConsumptionAmps: Float?) : DownloadStatus()
        data class Failed(val message: String?) : DownloadStatus()
        data class Enqueued(val workId: String) : DownloadStatus()
        object FetchingMetadata : DownloadStatus()
        data class MetadataFetched(val fileName: String, val fileLength: Long, val checkSum: String) : DownloadStatus()
        object AllDownloadsCompleted : DownloadStatus()
    }

    fun startBackendDrivenDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isDownloadingQueueActive.compareAndSet(false, true)) {
                Log.d("FileViewModel", "Starting backend-driven download queue.")
                fetchAndEnqueueNextFile()
            } else {
                Log.d("FileViewModel", "Download queue is already active. Ignoring request to start.")
            }
        }
    }

    private fun fetchAndEnqueueNextFile() {
        viewModelScope.launch {
            _downloadStatus.value = DownloadStatus.FetchingMetadata
            Log.d("FileViewModel", "Attempting to fetch next file metadata...")

            val result = fileDownloadRepository.getNextFileMetadata()

            result.onSuccess { metadata ->
                Log.d("FileViewModel", "Metadata fetched successfully: ${metadata.fileName}")
                _downloadStatus.value = DownloadStatus.MetadataFetched(metadata.fileName, metadata.fileLength, metadata.checkSum)
                enqueueFileDownloadWorker(metadata.fileName, metadata.fileLength, metadata.checkSum)

            }.onFailure { throwable ->
                val errorMessage = throwable.message
                if (errorMessage != null && errorMessage.contains("No more files available")) {
                    Log.d("FileViewModel", "All files downloaded.")
                    _downloadStatus.value = DownloadStatus.AllDownloadsCompleted
                } else {
                    Log.e("FileViewModel", "Failed to fetch file metadata: $errorMessage", throwable)
                    _downloadStatus.value = DownloadStatus.Failed(errorMessage)
                }
                _isDownloadingQueueActive.value = false
            }
        }
    }

    private fun enqueueFileDownloadWorker(fileName: String, fileLength: Long, checkSum: String) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_FILE_NAME, fileName)
            .putLong(DownloadWorker.KEY_FILE_LENGTH, fileLength)
            .putString(DownloadWorker.KEY_CHECKSUM, checkSum)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(0, java.util.concurrent.TimeUnit.SECONDS)
            .addTag(fileName)
            .build()

        workManager.enqueueUniqueWork(
            fileName,
            ExistingWorkPolicy.REPLACE,
            downloadRequest
        )

        _downloadStatus.value = DownloadStatus.Enqueued(downloadRequest.id.toString())
        observeDownloadProgress(downloadRequest.id)
        Log.d("FileViewModel", "Download worker enqueued for file: $fileName (Work ID: ${downloadRequest.id})")
    }

    private fun observeDownloadProgress(workId: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(workId)
                .asFlow()
                .filterNotNull()
                .collect { workInfo ->
                    Log.d("FileViewModel", "WorkInfo update: ${workInfo.state}, Progress: ${workInfo.progress.getInt("progress", 0)}")
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            _downloadStatus.value = DownloadStatus.Enqueued(workInfo.id.toString())
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt("progress", 0)
                            _downloadStatus.value = DownloadStatus.Progress(progress)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val powerConsumption = workInfo.outputData.getFloat(DownloadWorker.KEY_POWER_CONSUMPTION_AMPS, -1.0f)
                            val finalPowerConsumption: Float? = if (powerConsumption == -1.0f) null else powerConsumption

                            _downloadStatus.value = DownloadStatus.Completed(finalPowerConsumption)
                            fetchAndEnqueueNextFile()
                        }
                        WorkInfo.State.FAILED -> {
                            val errorMessage = workInfo.outputData.getString("error_message")
                            _downloadStatus.value = DownloadStatus.Failed(errorMessage)
                            _isDownloadingQueueActive.value = false
                        }
                        WorkInfo.State.CANCELLED -> {
                            _downloadStatus.value = DownloadStatus.Failed("Download cancelled.")
                            _isDownloadingQueueActive.value = false
                        }
                        WorkInfo.State.BLOCKED -> {
                            Log.d("FileViewModel", "Work is BLOCKED: ${workInfo.id}")
                        }
                    }
                }
        }
    }

    fun resetDownloadStatus() {
        _downloadStatus.value = DownloadStatus.Idle
        _isDownloadingQueueActive.value = false
    }
}
