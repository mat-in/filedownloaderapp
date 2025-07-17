package io.matin.filedownloader.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matin.filedownloader.repo.FileDownloadRepository
import io.matin.filedownloader.workers.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val fileDownloadRepository: FileDownloadRepository
) : ViewModel() {

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val DOWNLOAD_QUEUE_UNIQUE_WORK_NAME = "BackendDrivenDownloadQueue"

    init {
        viewModelScope.launch {
            _baseUrl.collectLatest { url ->
                Log.d("FileViewModel", "Base URL updated in ViewModel: $url")
                fileDownloadRepository.setBaseUrl(url)
            }
        }
        // IMPORTANT: On ViewModel initialization, check for existing work
        // This ensures the UI reflects ongoing downloads after app restart/process death
        checkExistingDownloads()
    }

    fun setBaseUrl(url: String) {
        if (_baseUrl.value != url) { // Only update if it's actually different
            _baseUrl.value = url
            Log.d("FileViewModel", "Base URL set to: $url (via setBaseUrl function)")
        }
    }

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

    /**
     * Checks for any existing WorkManager downloads associated with our queue.
     * Updates the UI status accordingly.
     */
    private fun checkExistingDownloads() {
        viewModelScope.launch {
            // Get all work info for our unique queue name
            val workInfos = workManager.getWorkInfosForUniqueWorkLiveData(DOWNLOAD_QUEUE_UNIQUE_WORK_NAME).asFlow()
            workInfos.collectLatest { infos ->
                val activeWork = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

                if (activeWork != null) {
                    // If there's active work, update the status and observe its progress
                    Log.d("FileViewModel", "Found existing active work: ${activeWork.id}, State: ${activeWork.state}")
                    _downloadStatus.value = DownloadStatus.Enqueued(activeWork.id.toString())
                    observeDownloadProgress(activeWork.id)
                } else {
                    // If no active work is found, and we weren't already completed/failed, go to Idle
                    if (_downloadStatus.value !is DownloadStatus.AllDownloadsCompleted &&
                        _downloadStatus.value !is DownloadStatus.Failed) {
                        _downloadStatus.value = DownloadStatus.Idle
                    }
                }
            }
        }
    }


    fun startBackendDrivenDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if URL is set
            if (_baseUrl.value.isBlank()) {
                Log.e("FileViewModel", "Cannot start download: Base URL is not set.")
                _downloadStatus.value = DownloadStatus.Failed("Backend URL not set. Please enter a URL.")
                return@launch
            }

            // Check if the queue is already active using WorkManager's unique work state
            val existingWorkStatus = workManager.getWorkInfosForUniqueWork(DOWNLOAD_QUEUE_UNIQUE_WORK_NAME).get()
            val isQueueAlreadyActive = existingWorkStatus.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }

            if (!isQueueAlreadyActive) {
                Log.d("FileViewModel", "Starting backend-driven download queue.")
                _downloadStatus.value = DownloadStatus.Loading
                fetchAndEnqueueNextFile()
            } else {
                Log.d("FileViewModel", "Download queue is already active. Ignoring request to start.")
                _downloadStatus.value = DownloadStatus.Enqueued(existingWorkStatus.first().id.toString())
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
            }
        }
    }

    private fun enqueueFileDownloadWorker(fileName: String, fileLength: Long, checkSum: String) {
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_FILE_NAME, fileName)
            .putLong(DownloadWorker.KEY_FILE_LENGTH, fileLength)
            .putString(DownloadWorker.KEY_CHECKSUM, checkSum)
            .putString(DownloadWorker.KEY_BASE_URL, _baseUrl.value)
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
            DOWNLOAD_QUEUE_UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
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
                    Log.d("FileViewModel", "WorkInfo update: ${workInfo.state}, Progress: ${workInfo.progress.getInt("progress", 0)} for Work ID: $workId")
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
                            workManager.cancelUniqueWork(DOWNLOAD_QUEUE_UNIQUE_WORK_NAME)
                        }
                        WorkInfo.State.CANCELLED -> {
                            _downloadStatus.value = DownloadStatus.Failed("Download cancelled.")
                            workManager.cancelUniqueWork(DOWNLOAD_QUEUE_UNIQUE_WORK_NAME)
                        }
                        WorkInfo.State.BLOCKED -> {
                            Log.d("FileViewModel", "Work is BLOCKED: ${workInfo.id}")
                        }
                    }
                }
        }
    }

    /**
     * Resets the download status to Idle and cancels any ongoing work for the queue.
     * Call this when the user explicitly stops the process or if you want to clear the queue state.
     */
    fun resetDownloadStatus() {
        _downloadStatus.value = DownloadStatus.Idle
        workManager.cancelUniqueWork(DOWNLOAD_QUEUE_UNIQUE_WORK_NAME)
        Log.d("FileViewModel", "Download status reset and queue work cancelled.")
    }

    override fun onCleared() {
        super.onCleared()
    }
}