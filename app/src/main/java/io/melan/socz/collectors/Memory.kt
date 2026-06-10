package io.melan.socz.collectors

import android.app.ActivityManager
import android.content.Context
import java.io.File

data class MemorySample(
    val totalKb: Long,
    val freeKb: Long,
    val availKb: Long,
    val cachedKb: Long,
    val buffersKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val zramKb: Long,
    val lowMemory: Boolean,
    val threshold: Long,
) {
    val usedKb: Long get() = totalKb - availKb
}

object MemoryCollector {
    fun sample(ctx: Context): MemorySample {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val meminfo = readMeminfo()
        return MemorySample(
            totalKb = meminfo["MemTotal"] ?: (mi.totalMem / 1024),
            freeKb = meminfo["MemFree"] ?: 0,
            availKb = meminfo["MemAvailable"] ?: (mi.availMem / 1024),
            cachedKb = meminfo["Cached"] ?: 0,
            buffersKb = meminfo["Buffers"] ?: 0,
            swapTotalKb = meminfo["SwapTotal"] ?: 0,
            swapFreeKb = meminfo["SwapFree"] ?: 0,
            zramKb = readZramTotal(),
            lowMemory = mi.lowMemory,
            threshold = mi.threshold / 1024,
        )
    }

    private fun readMeminfo(): Map<String, Long> {
        val m = HashMap<String, Long>()
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                val parts = line.split(':')
                if (parts.size == 2) {
                    val v = parts[1].trim().removeSuffix("kB").trim().toLongOrNull()
                    if (v != null) m[parts[0].trim()] = v
                }
            }
        }
        return m
    }

    private fun readZramTotal(): Long {
        var total = 0L
        for (i in 0..7) {
            val f = File("/sys/block/zram$i/disksize")
            if (!f.exists()) continue
            total += runCatching { f.readText().trim().toLong() / 1024 }.getOrDefault(0L)
        }
        return total
    }
}
