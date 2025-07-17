package io.matin.filedownloader.ui

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
import io.matin.filedownloader.data.BatteryLogDao
import io.matin.filedownloader.data.BatteryLogEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BatteryInfoFragment : Fragment() {

    @Inject
    lateinit var batteryLogDao: BatteryLogDao

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BatteryLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_battery_info, container, false)
        recyclerView = view.findViewById(R.id.battery_log_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = BatteryLogAdapter()
        recyclerView.adapter = adapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBatteryLogs()
    }

    private fun loadBatteryLogs() {
        lifecycleScope.launch {
            val logs = batteryLogDao.getAllBatteryLogs() // This query already orders by timestamp DESC
                .take(10) // Take only the 10 most recent entries
            adapter.submitList(logs)
        }
    }

    private class BatteryLogAdapter : RecyclerView.Adapter<BatteryLogAdapter.BatteryLogViewHolder>() {

        private val logs = mutableListOf<BatteryLogEntry>()
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun submitList(newLogs: List<BatteryLogEntry>) {
            logs.clear()
            logs.addAll(newLogs)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryLogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_battery_log, parent, false)
            return BatteryLogViewHolder(view)
        }

        override fun onBindViewHolder(holder: BatteryLogViewHolder, position: Int) {
            holder.bind(logs[position])
        }

        override fun getItemCount(): Int = logs.size

        inner class BatteryLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text_view)
            private val batteryPercentTextView: TextView = itemView.findViewById(R.id.battery_percent_text_view)
            private val chargingStatusTextView: TextView = itemView.findViewById(R.id.charging_status_text_view)
            private val powerConsumptionTextView: TextView = itemView.findViewById(R.id.power_consumption_text_view)

            fun bind(entry: BatteryLogEntry) {
                timestampTextView.text = dateFormatter.format(Date(entry.timestamp))
                batteryPercentTextView.text = "${entry.batteryPercentage}%"
                chargingStatusTextView.text = if (entry.isCharging) "Charging" else "Not Charging"
                powerConsumptionTextView.text = entry.powerConsumptionAmps?.let { String.format("%.2f A", it) } ?: "N/A"
            }
        }
    }
}