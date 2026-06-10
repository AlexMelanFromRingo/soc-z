package io.melan.socz.collectors

import java.io.File

/**
 * Per-core live state read from /sys/devices/system/cpu. On modern Android most
 * sysfs nodes are readable without root.
 */
data class CoreState(
    val index: Int,
    val online: Boolean,
    val curMhz: Long,
    val minMhz: Long,
    val maxMhz: Long,
    val governor: String,
    val utilization: Float?,           // 0..1 over last sample interval, null if unknown
)

data class CpuSample(
    val cores: List<CoreState>,
    val tempMilliC: Map<String, Long>,    // zone-name → milli-C
)

object CpuCollector {
    private var lastJiffies: List<LongArray>? = null

    fun sample(): CpuSample {
        val cores = (0..23).mapNotNull { readCore(it) }
        val temps = readThermalZones()
        return CpuSample(cores, temps)
    }

    private fun readCore(cpu: Int): CoreState? {
        val base = File("/sys/devices/system/cpu/cpu$cpu")
        if (!base.exists()) return null
        val freqDir = File(base, "cpufreq")
        val onlineFile = File(base, "online")
        val online = if (onlineFile.exists())
            runCatching { onlineFile.readText().trim() == "1" }.getOrDefault(true)
        else true

        fun khz(path: String): Long =
            runCatching { File(freqDir, path).readText().trim().toLong() }.getOrDefault(0L)
        val cur = khz("scaling_cur_freq")
        val min = khz("cpuinfo_min_freq")
        val max = khz("cpuinfo_max_freq")
        val governor = runCatching {
            File(freqDir, "scaling_governor").readText().trim()
        }.getOrDefault("?")

        // Utilization derived from /proc/stat would need delta computation across samples;
        // we expose null when we can't compute. (Kept simple for v1.)
        return CoreState(
            index = cpu, online = online,
            curMhz = cur / 1000, minMhz = min / 1000, maxMhz = max / 1000,
            governor = governor, utilization = null,
        )
    }

    private fun readThermalZones(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (i in 0..30) {
            val dir = File("/sys/class/thermal/thermal_zone$i")
            if (!dir.exists()) continue
            val type = runCatching { File(dir, "type").readText().trim() }.getOrDefault("zone$i")
            val temp = runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()
                ?: continue
            out[type] = temp
        }
        return out
    }
}
