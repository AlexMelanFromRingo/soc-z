package io.melan.socz.collectors

import android.os.Build
import java.io.File

/**
 * Static SoC + device descriptors. Read once at app start — none of these change
 * during the lifetime of the process, so we don't waste a ticker on them.
 */
data class SocInfo(
    val socModel: String,
    val socManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val androidSdk: Int,
    val abis: List<String>,
    val cpuImplementer: String,
    val cpuPart: String,
    val cpuArchitecture: String,
    val cpuFeatures: List<String>,
    val buildHardware: String,
    val buildPlatform: String,
    val coreCount: Int,
)

object SocCollector {
    fun read(): SocInfo {
        val cpuinfo = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        val firstBlock = cpuinfo.split("\n\n").firstOrNull().orEmpty()
        fun field(name: String) = firstBlock.lineSequence()
            .firstOrNull { it.startsWith(name) }?.substringAfter(":")?.trim().orEmpty()

        val features = field("Features").split(' ').filter { it.isNotBlank() }
        return SocInfo(
            socModel = Build.SOC_MODEL.takeUnless { it.isNullOrBlank() || it == "unknown" }
                ?: getSysProp("ro.soc.model") ?: getSysProp("ro.board.platform") ?: "unknown",
            socManufacturer = Build.SOC_MANUFACTURER.takeUnless { it.isNullOrBlank() || it == "unknown" }
                ?: getSysProp("ro.soc.manufacturer") ?: "unknown",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            cpuImplementer = field("CPU implementer"),
            cpuPart = field("CPU part"),
            cpuArchitecture = field("CPU architecture"),
            cpuFeatures = features,
            buildHardware = Build.HARDWARE,
            buildPlatform = getSysProp("ro.board.platform") ?: "unknown",
            coreCount = Runtime.getRuntime().availableProcessors(),
        )
    }

    private fun getSysProp(name: String): String? = try {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) { null }
}
