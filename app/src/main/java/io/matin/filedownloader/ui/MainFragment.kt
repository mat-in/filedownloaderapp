package io.matin.filedownloader.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.matin.filedownloader.utils.ErrorLogCollector
import io.matin.filedownloader.utils.StorageUtils
import io.matin.filedownloader.viewmodel.FileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    interface MainFragmentListener {
        fun onDownloadInitiated(url: String)
        fun onStoragePermissionNeeded()
        fun onNotificationPermissionNeeded()
    }

    private var listener: MainFragmentListener? = null

    private var _binding: io.matin.filedownloader.databinding.FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView


    private val fileViewModel: FileViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainFragmentListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement MainFragmentListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = io.matin.filedownloader.databinding.FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val downloadButton = binding.downloadButton
        val statusTextView = binding.statusTextView
        val urlEditText = binding.urlEditText
        val totalStorageTextView = binding.totalStorageTextView
        val freeSpaceTextView = binding.freeSpaceTextView

        logScrollView = binding.logScrollView
        logTextView = binding.logTextView

        downloadButton.setOnClickListener {
            if (fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.Idle ||
                fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.Failed ||
                fileViewModel.downloadStatus.value is FileViewModel.DownloadStatus.AllDownloadsCompleted) {
                // Let MainActivity handle permissions and initiate download
                listener?.onNotificationPermissionNeeded()
            } else {
                Toast.makeText(context, "Download already in progress or enqueued.", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileViewModel.downloadStatus.collect { status ->
                    Log.d("MainFragment", "DownloadStatus observed: $status")
                    when (status) {
                        FileViewModel.DownloadStatus.Idle -> {
                            statusTextView.text = "Status: Ready to fetch file metadata."
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                        }
                        is FileViewModel.DownloadStatus.Enqueued -> {
                            statusTextView.text = "Download enqueued (Work ID: ${status.workId})"
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                        }
                        FileViewModel.DownloadStatus.Loading -> {
                            statusTextView.text = "Status: Initializing download..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                        }
                        is FileViewModel.DownloadStatus.Progress -> {
                            statusTextView.text = "Downloading: ${status.percentage}%"
                            downloadButton.isEnabled = false
                        }
                        is FileViewModel.DownloadStatus.Completed -> {
                            statusTextView.text = "Download successful! Checking for next file..."
                            updateStorageInfo()
                        }
                        is FileViewModel.DownloadStatus.Failed -> {
                            statusTextView.text = "Download failed: ${status.message ?: "Unknown error"}"
                            Log.e("MainFragment", "Download failed: ${status.message ?: "Unknown error"}")
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                        }
                        FileViewModel.DownloadStatus.FetchingMetadata -> {
                            statusTextView.text = "Status: Fetching file metadata from backend..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                        }
                        is FileViewModel.DownloadStatus.MetadataFetched -> {
                            statusTextView.text = "Status: Metadata fetched for ${status.fileName}. Enqueuing download..."
                            downloadButton.isEnabled = false
                            urlEditText.isEnabled = false
                        }
                        FileViewModel.DownloadStatus.AllDownloadsCompleted -> {
                            statusTextView.text = "All files downloaded successfully!"
                            downloadButton.isEnabled = true
                            urlEditText.isEnabled = true
                            updateStorageInfo()
                        }
                    }
                }
            }
        }

        ErrorLogCollector.logMessages.observe(viewLifecycleOwner, Observer { logs ->
            logTextView.text = logs
            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        })

        updateStorageInfo()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up binding
    }

    fun getEnteredUrl(): String {
        return binding.urlEditText.text.toString().trim()
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun updateStorageInfo() {
        lifecycleScope.launch(Dispatchers.Main) {
            val (totalInternal, freeInternal) = StorageUtils.getInternalStorageInfo()
            binding.totalStorageTextView.text = "Total Internal Storage: ${StorageUtils.formatFileSize(totalInternal)}"
            binding.freeSpaceTextView.text = "Free Internal Space: ${StorageUtils.formatFileSize(freeInternal)}"
        }
    }

    companion object {
        fun newInstance() = MainFragment()
        private const val TAG = "MainFragment"
    }
}