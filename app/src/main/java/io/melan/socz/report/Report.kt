package io.melan.socz.report

import android.content.Context
import io.melan.socz.collectors.BatteryCollector
import io.melan.socz.collectors.CpuCollector
import io.melan.socz.collectors.DisplayCollector
import io.melan.socz.collectors.GpuCollector
import io.melan.socz.collectors.MemoryCollector
import io.melan.socz.collectors.NetworkCollector
import io.melan.socz.collectors.SensorCollector
import io.melan.socz.collectors.SocCollector
import io.melan.socz.collectors.StorageCollector
import java.text.DateFormat
import java.util.Date

/**
 * Builds the plain-text hardware report behind the Overview share action.
 * Deliberately English-only: reports get pasted into bug trackers and forums,
 * where a locale-independent dump is easier to compare across devices.
 *
 * Call on Dispatchers.IO — it runs every collector, including the EGL/Vulkan
 * GPU probe.
 */
object ReportBuilder {
    private const val GIB = 1024L * 1024L * 1024L

    fun build(ctx: Context): String = buildString {
        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
        appendLine("SOC-Z $version hardware report — ${DateFormat.getDateTimeInstance().format(Date())}")

        val soc = SocCollector.read()
        section("Device")
        kv("Model", soc.deviceModel)
        kv("SoC", "${soc.socManufacturer} ${soc.socModel}")
        kv("Android", "${soc.androidVersion} (SDK ${soc.androidSdk})")
        kv("ABIs", soc.abis.joinToString(", "))
        kv("Hardware", soc.buildHardware)
        kv("Platform", soc.buildPlatform)
        kv("Cores", soc.coreCount.toString())
        kv("CPU features", soc.cpuFeatures.joinToString(" "))

        val cpu = CpuCollector.sample()
        section("CPU")
        cpu.cores.forEach { c ->
            kv("cpu${c.index}", "${c.curMhz} MHz (${c.minMhz}–${c.maxMhz} MHz), " +
                "${if (c.online) "online" else "offline"}, ${c.governor}")
        }
        cpu.tempMilliC.forEach { (zone, milliC) -> kv(zone, "%.1f °C".format(milliC / 1000f)) }

        section("GPU")
        runCatching { GpuCollector.read(ctx) }.getOrNull()?.let { gpu ->
            kv("Renderer", gpu.openGlRenderer)
            kv("Vendor", gpu.openGlVendor)
            kv("OpenGL", gpu.openGlVersion)
            kv("GLSL", gpu.glslVersion)
            kv("Vulkan", gpu.vulkanApiVersion ?: "not supported")
            kv("Ray tracing pipeline", yn(gpu.supportsRayTracingPipeline))
            kv("Acceleration structure", yn(gpu.supportsAccelerationStructure))
            kv("Ray query", yn(gpu.supportsRayQuery))
            kv("Mesh shader", yn(gpu.supportsMeshShader))
            kv("Bindless textures", yn(gpu.supportsBindlessTextures))
            kv("Vulkan extensions", gpu.vulkanExtensions.size.toString())
            kv("OpenGL extensions", gpu.openGlExtensions.size.toString())
        }
        GpuCollector.sampleFrequency()?.let { f ->
            kv("Clock", "${f.curMhz} MHz (${f.minMhz}–${f.maxMhz} MHz)" +
                (f.busyPercent?.let { ", busy $it%" } ?: ""))
        }

        val mem = MemoryCollector.sample(ctx)
        section("Memory")
        kv("Total", "${mem.totalKb / 1024} MiB")
        kv("Used", "${mem.usedKb / 1024} MiB")
        kv("Available", "${mem.availKb / 1024} MiB")
        kv("Swap", "${(mem.swapTotalKb - mem.swapFreeKb) / 1024} / ${mem.swapTotalKb / 1024} MiB")
        kv("ZRAM", "${mem.zramKb / 1024} MiB")

        val b = BatteryCollector.sample(ctx)
        section("Battery")
        kv("Level", "${b.levelPercent}%")
        kv("Status", "${b.status} (${b.plugged})")
        kv("Temperature", "%.1f °C".format(b.temperatureC))
        kv("Current", "${b.currentUa / 1000} mA")
        kv("Voltage", "${b.voltageMv} mV")
        kv("Charge counter", "${b.capacityRemainUah / 1000} mAh")
        b.designCapacityMah?.let { kv("Design capacity", "${it.toInt()} mAh") }
        if (b.chargingCycles >= 0) kv("Cycles", b.chargingCycles.toString())
        kv("Health", b.health)
        kv("Technology", b.technology)

        section("Displays")
        DisplayCollector.read(ctx).forEach { d ->
            kv(d.name, "${d.widthPx}×${d.heightPx} @ ${"%.0f".format(d.refreshHz)} Hz, " +
                "${d.densityDpi} dpi" +
                (if (d.hdrTypes.isNotEmpty()) ", HDR: ${d.hdrTypes.joinToString("/")}" else ""))
        }

        section("Storage")
        StorageCollector.read(ctx).forEach { v ->
            kv(v.label, "${v.usedBytes / GIB} / ${v.totalBytes / GIB} GiB (${v.path})")
        }

        val sensors = SensorCollector.read(ctx)
        section("Sensors (${sensors.size})")
        sensors.forEach { s ->
            kv(s.typeName.removePrefix("android.sensor."), "${s.name} — ${s.vendor}")
        }

        val n = NetworkCollector.sample(ctx)
        section("Network")
        kv("Transport", n.activeTransport)
        n.wifiSsid?.let { kv("SSID", it) }
        n.wifiStandard?.let { kv("Standard", it) }
        n.wifiLinkSpeedMbps?.let { kv("Link", "$it Mbps") }
        n.cellularOperator?.let { kv("Operator", it) }
        n.cellularType?.let { kv("Network", it) }
        n.ipv4?.let { kv("IPv4", it) }
        n.ipv6?.let { kv("IPv6", it) }
    }

    private fun StringBuilder.section(name: String) {
        appendLine()
        appendLine("== $name ==")
    }

    private fun StringBuilder.kv(k: String, v: String) = appendLine("$k: $v")

    private fun yn(b: Boolean) = if (b) "yes" else "no"
}
