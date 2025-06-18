package io.matin.filedownloader.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

class BatteryInfoReceiver(private val listener: BatteryUpdateListener) : BroadcastReceiver() {

    interface BatteryUpdateListener {
        fun onBatteryInfoUpdated(percentage: Int, isCharging: Boolean)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct: Int = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            Log.d("BatteryInfoReceiver", "Battery Updated: $batteryPct%, Charging: $isCharging")
            listener.onBatteryInfoUpdated(batteryPct, isCharging)
        }
    }
}