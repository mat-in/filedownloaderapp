package io.matin.filedownloader.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.matin.filedownloader.data.BatteryLogDao
import io.matin.filedownloader.data.BatteryLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BatteryInfoReceiver : BroadcastReceiver() {

    @Inject
    lateinit var batteryLogDao: BatteryLogDao

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface BatteryUpdateListener {
        fun onBatteryInfoUpdated(percentage: Int, isCharging: Boolean)
    }

    // Listener is generally not needed if data is saved to DB and then observed from UI
    // If you still need an immediate callback to MainActivity for non-DB related updates, keep it.
    // private var listener: BatteryUpdateListener? = null
    // fun setBatteryUpdateListener(listener: BatteryUpdateListener) {
    //     this.listener = listener
    // }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val voltage: Int = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val temperature: Int = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) // in tenths of a degree Celsius
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val health: Int = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            val plugged: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val technology: String? = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

            val batteryPct: Float = if (scale > 0) level * 100 / scale.toFloat() else 0f
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Log.d("BatteryReceiver", "Battery Level: $batteryPct%, Charging: $isCharging, Status: $status, Plugged: $plugged, Health: $health, Voltage: $voltage mV, Temp: ${temperature / 10.0} °C, Tech: $technology")

            scope.launch {
                val batteryLogEntry = BatteryLogEntry(
                    batteryPercentage = batteryPct,
                    isCharging = isCharging,
                    chargingStatus = status,
                    pluggedStatus = plugged,
                    healthStatus = health,
                    voltage = voltage,
                    temperature = temperature,
                    technology = technology
                )
                try {
                    batteryLogDao.insertBatteryLog(batteryLogEntry)
                    Log.d("BatteryReceiver", "Battery log saved to DB.")
                } catch (e: Exception) {
                    Log.e("BatteryReceiver", "Failed to save battery log to DB: ${e.message}", e)
                }
            }
        }
    }
}