package io.matin.filedownloader.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.matin.filedownloader.R
import io.matin.filedownloader.data.WifiLogDao
import io.matin.filedownloader.data.WifiLogEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WifiInfoFragment : Fragment() {

    @Inject
    lateinit var wifiLogDao: WifiLogDao

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WifiLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wifi_info, container, false)
        recyclerView = view.findViewById(R.id.wifi_log_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = WifiLogAdapter()
        recyclerView.adapter = adapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadWifiLogs()
    }

    private fun loadWifiLogs() {
        lifecycleScope.launch {
            val logs = wifiLogDao.getRecentWifiLogs()
            adapter.submitList(logs)
        }
    }

    private class WifiLogAdapter : RecyclerView.Adapter<WifiLogAdapter.WifiLogViewHolder>() {

        private val logs = mutableListOf<WifiLogEntry>()
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun submitList(newLogs: List<WifiLogEntry>) {
            logs.clear()
            logs.addAll(newLogs)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiLogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi_log, parent, false)
            return WifiLogViewHolder(view)
        }

        override fun onBindViewHolder(holder: WifiLogViewHolder, position: Int) {
            holder.bind(logs[position])
        }

        override fun getItemCount(): Int = logs.size

        inner class WifiLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestampTextView: TextView = itemView.findViewById(R.id.wifi_timestamp_text_view)
            private val rssiTextView: TextView = itemView.findViewById(R.id.wifi_rssi_text_view)
            private val linkSpeedTextView: TextView = itemView.findViewById(R.id.wifi_link_speed_text_view)
            private val frequencyTextView: TextView = itemView.findViewById(R.id.wifi_frequency_text_view)
            private val standardTextView: TextView = itemView.findViewById(R.id.wifi_standard_text_view)

            fun bind(entry: WifiLogEntry) {
                timestampTextView.text = dateFormatter.format(Date(entry.timestamp))
                rssiTextView.text = "${entry.rssi} dBm"
                linkSpeedTextView.text = "${entry.linkSpeedMbps} Mbps"
                frequencyTextView.text = "${entry.frequencyMHz} MHz"

                // Using direct integer values to bypass unresolved reference issues
                standardTextView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    when (entry.wifiStandard) {
                        6 -> "802.11ax" // WIFI_STANDARD_11AX
                        4 -> "802.11ac" // WIFI_STANDARD_11AC
                        3 -> "802.11n" // WIFI_STANDARD_11N
                        2 -> "802.11g" // WIFI_STANDARD_11G
                        1 -> "802.11a/Legacy" // WIFI_STANDARD_11A & WIFI_STANDARD_LEGACY
                        5 -> "802.11b" // WIFI_STANDARD_11B
                        else -> "Unknown"
                    }
                } else {
                    "N/A (<Android 10)"
                }
            }
        }
    }
}