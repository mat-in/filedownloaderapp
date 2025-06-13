package io.matin.filedownloader.viewmodel

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
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

@HiltViewModel
class FileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) : ViewModel() {

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        object Loading : DownloadStatus()
        data class Progress(val percentage: Int) : DownloadStatus()
        object Completed : DownloadStatus()
        data class Failed(val message: String?) : DownloadStatus()
        data class Enqueued(val workId: String) : DownloadStatus()
    }

    fun startDownload(url: String, fileName: String) {
        _downloadStatus.value = DownloadStatus.Loading

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_FILE_URL, url)
            .putString(DownloadWorker.KEY_FILE_NAME, fileName)
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
        Log.d("FileViewModel", "Download enqueued: ${downloadRequest.id}")
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
                            _downloadStatus.value = DownloadStatus.Completed
                        }
                        WorkInfo.State.FAILED -> {
                            val errorMessage = workInfo.outputData.getString("error_message")
                            _downloadStatus.value = DownloadStatus.Failed(errorMessage)
                        }
                        WorkInfo.State.CANCELLED -> {
                            _downloadStatus.value = DownloadStatus.Failed("Download cancelled.")
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
    }
}