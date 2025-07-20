// io.matin.filedownloader.ui.BatteryInfoFragment.kt
package io.matin.filedownloader.ui

import android.os.BatteryManager
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
import kotlinx.coroutines.flow.collectLatest // For observing Flow from Room
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BatteryInfoFragment : Fragment() {

    @Inject
    lateinit var batteryLogDao: BatteryLogDao // Injected by Hilt

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
        observeBatteryLogs() // Changed to observe
    }

    private fun observeBatteryLogs() { // Changed to observe
        lifecycleScope.launch {
            // Observe the Flow from Room for real-time updates
            batteryLogDao.getAllBatteryLogsFlow()
                .collectLatest { logs ->
                    adapter.submitList(logs.take(50)) // Display up to 50 latest entries
                }
        }
    }

    private class BatteryLogAdapter : RecyclerView.Adapter<BatteryLogAdapter.BatteryLogViewHolder>() {

        private val logs = mutableListOf<BatteryLogEntry>()
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun submitList(newLogs: List<BatteryLogEntry>) {
            // Use DiffUtil if logs can be very large for better performance
            // For 10-50 entries, notifyDataSetChanged is likely fine.
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

            // Add new TextViews for other battery parameters in item_battery_log.xml if you want to display them
            private val voltageTextView: TextView = itemView.findViewById(R.id.voltage_text_view) // Assuming you'll add these
            private val temperatureTextView: TextView = itemView.findViewById(R.id.temperature_text_view)
            private val healthStatusTextView: TextView = itemView.findViewById(R.id.health_status_text_view)
            private val pluggedStatusTextView: TextView = itemView.findViewById(R.id.plugged_status_text_view)
            private val technologyTextView: TextView = itemView.findViewById(R.id.technology_text_view)


            fun bind(entry: BatteryLogEntry) {
                timestampTextView.text = dateFormatter.format(Date(entry.timestamp))
                batteryPercentTextView.text = "Battery: ${entry.batteryPercentage}%"
                chargingStatusTextView.text = "Charging: ${if (entry.isCharging) "Yes" else "No"} (Status: ${getBatteryStatusString(entry.chargingStatus)})"
                powerConsumptionTextView.text = entry.powerConsumptionAmps?.let { String.format("Power: %.2f A", it) } ?: "Power: N/A"

                // Bind new battery parameters
                voltageTextView.text = "Voltage: ${entry.voltage} mV"
                temperatureTextView.text = "Temp: ${entry.temperature / 10.0} °C"
                healthStatusTextView.text = "Health: ${getBatteryHealthString(entry.healthStatus)}"
                pluggedStatusTextView.text = "Plugged: ${getBatteryPluggedString(entry.pluggedStatus)}"
                technologyTextView.text = "Tech: ${entry.technology ?: "N/A"}"
            }

            // Helper functions to convert integer constants to readable strings
            private fun getBatteryStatusString(status: Int): String {
                return when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }
            }

            private fun getBatteryHealthString(health: Int): String {
                return when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }
            }

            private fun getBatteryPluggedString(plugged: Int): String {
                return when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Not Plugged"
                }
            }
        }
    }
}