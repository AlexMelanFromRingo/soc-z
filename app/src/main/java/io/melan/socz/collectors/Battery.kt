package io.melan.socz.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatterySample(
    val levelPercent: Int,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentUa: Long,         // can be negative (discharging) or positive (charging)
    val capacityRemainUah: Long, // remaining capacity in µAh (CHARGE_COUNTER)
    val energyCounterNwh: Long,  // remaining energy in nWh (ENERGY_COUNTER), -1 if unsupported
    val status: String,
    val plugged: String,
    val technology: String,
    val health: String,
    val chargingCycles: Int,
    /** Factory design capacity in mAh from the OEM power profile, null if unavailable. */
    val designCapacityMah: Double?,
)

object BatteryCollector {
    // PowerProfile lives in internal API; probe it once per process via reflection
    // and cache the result (the design capacity obviously never changes).
    private var designCapacityCache: Double? = null
    private var designCapacityProbed = false

    private fun designCapacityMah(ctx: Context): Double? {
        if (!designCapacityProbed) {
            designCapacityProbed = true
            designCapacityCache = runCatching {
                val cls = Class.forName("com.android.internal.os.PowerProfile")
                val profile = cls.getConstructor(Context::class.java).newInstance(ctx)
                cls.getMethod("getBatteryCapacity").invoke(profile) as Double
            }.getOrNull()?.takeIf { it > 100.0 } // some OEMs report 0 or 1000-stub
        }
        return designCapacityCache
    }

    fun sample(ctx: Context): BatterySample {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentUa = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val capacityRemain = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energyCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        // Cycle count arrives as an ACTION_BATTERY_CHANGED extra from API 34.
        // (There is no BatteryManager property for it — raw id 6 is
        // BATTERY_PROPERTY_STATUS, which used to be queried here by mistake.)
        val cycles = if (android.os.Build.VERSION.SDK_INT >= 34)
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1 else -1

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

        return BatterySample(
            levelPercent = level,
            temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            currentUa = currentUa,
            capacityRemainUah = capacityRemain,
            energyCounterNwh = energyCounter,
            status = statusName(statusInt),
            plugged = pluggedName(pluggedInt),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?",
            health = healthName(healthInt),
            chargingCycles = cycles,
            designCapacityMah = designCapacityMah(ctx),
        )
    }

    private fun statusName(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun pluggedName(p: Int) = when (p) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        0 -> "Unplugged"
        else -> "?"
    }

    private fun healthName(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }
}
