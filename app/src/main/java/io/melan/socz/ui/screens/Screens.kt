package io.melan.socz.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.melan.socz.collectors.BatteryCollector
import io.melan.socz.collectors.CpuCollector
import io.melan.socz.collectors.DisplayCollector
import io.melan.socz.collectors.GpuCollector
import io.melan.socz.collectors.MemoryCollector
import io.melan.socz.collectors.NetworkCollector
import io.melan.socz.collectors.SensorCollector
import io.melan.socz.collectors.SocCollector
import io.melan.socz.collectors.SocInfo
import io.melan.socz.collectors.StorageCollector
import io.melan.socz.collectors.tickerFlow
import io.melan.socz.ui.components.KvCard
import io.melan.socz.ui.components.MetricBar
import io.melan.socz.ui.components.SectionTitle
import io.melan.socz.ui.components.VSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseScreen(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(title) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        content()
    }
}

@Composable
fun OverviewScreen(onOpenSecondary: (String) -> Unit) {
    val ctx = LocalContext.current
    val soc by produceState<SocInfo?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { SocCollector.read() }
    }
    val cpuTick = remember { tickerFlow(1000L) { CpuCollector.sample() }.flowOn(Dispatchers.IO) }
    val cpuSample by cpuTick.collectAsState(initial = null)
    val batterySample by remember(ctx) {
        tickerFlow(2000L) { BatteryCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    val memorySample by remember(ctx) {
        tickerFlow(1500L) { MemoryCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)

    BaseScreen("SOC-Z") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4); SectionTitle("Device") }
            item {
                val s = soc ?: return@item
                KvCard(rows = listOf(
                    "Model" to s.deviceModel,
                    "SoC" to "${s.socManufacturer} ${s.socModel}",
                    "Android" to "${s.androidVersion} (SDK ${s.androidSdk})",
                    "Cores" to s.coreCount.toString(),
                    "ABIs" to s.abis.joinToString(", "),
                    "Hardware" to s.buildHardware,
                    "Platform" to s.buildPlatform,
                ))
            }
            item { SectionTitle("Live") }
            item {
                val cpu = cpuSample
                if (cpu != null) {
                    val avgMhz = cpu.cores.filter { it.online }
                        .map { it.curMhz }.average().toLong()
                    val maxMhz = cpu.cores.maxOfOrNull { it.maxMhz } ?: 0L
                    KvCard(
                        rows = listOf(
                            "CPU avg" to "$avgMhz MHz (peak $maxMhz MHz)",
                            "CPU temps" to cpu.tempMilliC.entries.joinToString(", ") {
                                "${it.key}: ${"%.1f".format(it.value / 1000f)}°C"
                            }.ifBlank { "—" },
                        ),
                        accent = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                val mem = memorySample
                if (mem != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Memory", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            MetricBar(
                                label = "RAM",
                                valueText = "${mem.usedKb / 1024} / ${mem.totalKb / 1024} MiB",
                                fraction = mem.usedKb.toFloat() / mem.totalKb.coerceAtLeast(1L),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                            if (mem.swapTotalKb > 0) {
                                MetricBar(
                                    label = "Swap (ZRAM)",
                                    valueText = "${(mem.swapTotalKb - mem.swapFreeKb) / 1024} / " +
                                        "${mem.swapTotalKb / 1024} MiB",
                                    fraction = (mem.swapTotalKb - mem.swapFreeKb).toFloat() /
                                        mem.swapTotalKb.coerceAtLeast(1L),
                                    accent = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
            item {
                val b = batterySample ?: return@item
                KvCard(
                    title = "Battery",
                    accent = MaterialTheme.colorScheme.secondary,
                    rows = listOf(
                        "Level" to "${b.levelPercent}%",
                        "Status" to "${b.status} (${b.plugged})",
                        "Temperature" to "${"%.1f".format(b.temperatureC)} °C",
                        "Current" to "${b.currentUa / 1000} mA",
                        "Voltage" to "${b.voltageMv} mV",
                    ),
                )
            }
            item { SectionTitle("Other") }
            item { SecondaryNavRow(Icons.Outlined.SmartDisplay, "Display", { onOpenSecondary("display") }) }
            item { SecondaryNavRow(Icons.Outlined.Storage, "Storage", { onOpenSecondary("storage") }) }
            item { SecondaryNavRow(Icons.Outlined.Sensors, "Sensors", { onOpenSecondary("sensors") }) }
            item { SecondaryNavRow(Icons.Outlined.NetworkCell, "Network", { onOpenSecondary("network") }) }
            item { VSpace(16) }
        }
    }
}

@Composable
private fun SecondaryNavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ============================ CPU ============================ */

@Composable
fun CpuScreen() {
    val sample by remember {
        tickerFlow(500L) { CpuCollector.sample() }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen("CPU") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { VSpace(4); SectionTitle("Cores") }
            val cpu = sample
            if (cpu != null) {
                items(cpu.cores) { core ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("CPU ${core.index}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(80.dp))
                                Text(if (core.online) "online" else "offline",
                                    color = if (core.online) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.weight(1f))
                                Text("${core.curMhz} MHz",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(4.dp))
                            MetricBar(
                                label = "${core.minMhz} – ${core.maxMhz} MHz  · ${core.governor}",
                                valueText = "${((core.curMhz - core.minMhz) * 100 /
                                    (core.maxMhz - core.minMhz).coerceAtLeast(1L)).coerceIn(0L, 100L)}%",
                                fraction = (core.curMhz - core.minMhz).toFloat() /
                                    (core.maxMhz - core.minMhz).coerceAtLeast(1L).toFloat(),
                            )
                        }
                    }
                }
                item { SectionTitle("Thermal zones") }
                item {
                    KvCard(rows = cpu.tempMilliC.entries.map { (k, v) ->
                        k to "${"%.1f".format(v / 1000f)} °C"
                    }.ifEmpty { listOf("(none)" to "—") })
                }
            }
            item { VSpace(16) }
        }
    }
}

/* ============================ GPU ============================ */

@Composable
fun GpuScreen() {
    val ctx = LocalContext.current
    val gpu by produceState<io.melan.socz.collectors.GpuInfo?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { GpuCollector.read(ctx) }
    }
    BaseScreen("GPU") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            val g = gpu ?: return@LazyColumn
            item {
                KvCard(
                    title = "OpenGL ES",
                    accent = MaterialTheme.colorScheme.primary,
                    rows = listOf(
                        "Vendor" to g.openGlVendor,
                        "Renderer" to g.openGlRenderer,
                        "Version" to g.openGlVersion,
                        "GLSL" to g.glslVersion,
                        "Adreno" to (g.adrenoModel?.toString() ?: "—"),
                    ),
                )
            }
            item {
                KvCard(
                    title = "Vulkan",
                    accent = MaterialTheme.colorScheme.tertiary,
                    rows = listOf(
                        "API version" to (g.vulkanApiVersion ?: "not supported"),
                        "Ray tracing pipeline" to flag(g.supportsRayTracingPipeline),
                        "Acceleration structure" to flag(g.supportsAccelerationStructure),
                        "Ray query" to flag(g.supportsRayQuery),
                        "Mesh shader" to flag(g.supportsMeshShader),
                        "Bindless textures" to flag(g.supportsBindlessTextures),
                    ),
                )
            }
            item { SectionTitle("Vulkan extensions (${g.vulkanExtensions.size})") }
            items(g.vulkanExtensions) { e ->
                Text(e, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp))
            }
            item { SectionTitle("OpenGL extensions (${g.openGlExtensions.size})") }
            items(g.openGlExtensions) { e ->
                Text(e, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp))
            }
            item { VSpace(16) }
        }
    }
}

private fun flag(b: Boolean) = if (b) "✓ yes" else "✗ no"

/* ============================ MEMORY ============================ */

@Composable
fun MemoryScreen() {
    val ctx = LocalContext.current
    val sample by remember(ctx) {
        tickerFlow(1000L) { MemoryCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen("Memory") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            val m = sample ?: return@LazyColumn
            item {
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MetricBar("RAM used",
                            "${m.usedKb / 1024} / ${m.totalKb / 1024} MiB",
                            m.usedKb.toFloat() / m.totalKb.coerceAtLeast(1L),
                            MaterialTheme.colorScheme.primary)
                        MetricBar("Cached",
                            "${m.cachedKb / 1024} MiB",
                            m.cachedKb.toFloat() / m.totalKb.coerceAtLeast(1L),
                            MaterialTheme.colorScheme.tertiary)
                        if (m.swapTotalKb > 0) {
                            MetricBar("Swap/ZRAM",
                                "${(m.swapTotalKb - m.swapFreeKb) / 1024} / ${m.swapTotalKb / 1024} MiB",
                                (m.swapTotalKb - m.swapFreeKb).toFloat() /
                                    m.swapTotalKb.coerceAtLeast(1L),
                                MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            item {
                KvCard(rows = listOf(
                    "MemTotal" to "${m.totalKb / 1024} MiB",
                    "MemFree" to "${m.freeKb / 1024} MiB",
                    "MemAvailable" to "${m.availKb / 1024} MiB",
                    "Cached" to "${m.cachedKb / 1024} MiB",
                    "Buffers" to "${m.buffersKb / 1024} MiB",
                    "SwapTotal" to "${m.swapTotalKb / 1024} MiB",
                    "SwapFree" to "${m.swapFreeKb / 1024} MiB",
                    "ZRAM" to "${m.zramKb / 1024} MiB",
                    "Low memory" to if (m.lowMemory) "ALERT" else "no",
                    "Threshold" to "${m.threshold / 1024} MiB",
                ))
            }
            item { VSpace(16) }
        }
    }
}

/* ============================ BATTERY ============================ */

@Composable
fun BatteryScreen() {
    val ctx = LocalContext.current
    val sample by remember(ctx) {
        tickerFlow(1000L) { BatteryCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen("Battery") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            val b = sample ?: return@LazyColumn
            item {
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bolt, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("${b.levelPercent}%",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.weight(1f))
                            Text(b.status, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        MetricBar(
                            label = "Level",
                            valueText = "${b.levelPercent}%",
                            fraction = b.levelPercent / 100f,
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            item {
                KvCard(rows = listOf(
                    "Temperature" to "${"%.1f".format(b.temperatureC)} °C",
                    "Current" to "${b.currentUa / 1000} mA",
                    "Voltage" to "${b.voltageMv} mV",
                    "Power" to "${"%.2f".format(b.currentUa / 1_000_000.0 * b.voltageMv / 1000.0)} W",
                    "Charge counter" to "${b.capacityRemainUah / 1000} mAh",
                    "Cycles" to if (b.chargingCycles >= 0) b.chargingCycles.toString() else "—",
                    "Plugged" to b.plugged,
                    "Technology" to b.technology,
                    "Health" to b.health,
                ))
            }
            item { VSpace(16) }
        }
    }
}

/* ============================ SECONDARY SCREENS ============================ */

@Composable
fun DisplayScreen() {
    val ctx = LocalContext.current
    // Re-read every second: state and rotation (the display's current orientation,
    // 0/90/180/270° from its natural one) change at runtime.
    val displays by remember(ctx) {
        tickerFlow(1000L) { DisplayCollector.read(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = emptyList())
    BaseScreen("Display") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            items(displays) { d ->
                KvCard(
                    title = d.name,
                    rows = listOf(
                        "Resolution" to "${d.widthPx} × ${d.heightPx} px",
                        "Density" to "${d.densityDpi} dpi",
                        "Refresh" to "${"%.1f".format(d.refreshHz)} Hz",
                        "Supported" to d.supportedRefreshRates.joinToString(", ") { "${"%.0f".format(it)} Hz" },
                        "HDR" to d.hdrTypes.joinToString(", ").ifBlank { "—" },
                        "State" to d.state,
                        "Rotation" to "${d.rotationDeg}°",
                    ),
                )
            }
            item { VSpace(16) }
        }
    }
}

@Composable
fun StorageScreen() {
    val ctx = LocalContext.current
    val volumes by produceState(initialValue = emptyList<io.melan.socz.collectors.VolumeInfo>()) {
        value = withContext(Dispatchers.IO) { StorageCollector.read(ctx) }
    }
    BaseScreen("Storage") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            items(volumes) { v ->
                Card(shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(v.label, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(v.path, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        MetricBar(
                            label = "Used",
                            valueText = "${v.usedBytes / (1024L * 1024L * 1024L)} / " +
                                "${v.totalBytes / (1024L * 1024L * 1024L)} GiB",
                            fraction = v.usedBytes.toFloat() / v.totalBytes.coerceAtLeast(1L),
                        )
                        Text(
                            text = buildString {
                                if (v.isPrimary) append("primary  ")
                                if (v.isRemovable) append("removable  ")
                                if (v.isEmulated) append("emulated")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item { VSpace(16) }
        }
    }
}

@Composable
fun SensorsScreen() {
    val ctx = LocalContext.current
    val sensors by produceState(initialValue = emptyList<io.melan.socz.collectors.SensorDesc>()) {
        value = withContext(Dispatchers.IO) { SensorCollector.read(ctx) }
    }
    val liveValues by remember(ctx) {
        SensorCollector.liveValues(ctx)
    }.collectAsState(initial = emptyMap())
    BaseScreen("Sensors") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { VSpace(4); SectionTitle("Found: ${sensors.size}") }
            items(sensors) { s ->
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text(s.typeName, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace)
                        Text(
                            buildString {
                                append(s.vendor); append(" · ")
                                append("range ${s.maxRange}, res ${s.resolution}, ")
                                append("${s.powerMa} mA")
                                if (s.isWakeUp) append(", wakeup")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        liveValues[s.key]?.let { values ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                values.joinToString("  ", limit = 6) { "%.2f".format(it) },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            item { VSpace(16) }
        }
    }
}

@Composable
fun NetworkScreen() {
    val ctx = LocalContext.current
    // SSID is gated behind fine location, cellular network type behind phone state.
    // The 2 s ticker below re-samples, so values appear right after the grant.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val missing = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        ).filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    val sample by remember(ctx) {
        tickerFlow(2000L) { NetworkCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen("Network") {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            val n = sample ?: return@LazyColumn
            item {
                KvCard(
                    title = "Active connection",
                    accent = MaterialTheme.colorScheme.primary,
                    rows = listOfNotNull(
                        "Transport" to n.activeTransport,
                        "Downstream" to "${n.downstreamKbps / 1000} Mbps",
                        "Upstream" to "${n.upstreamKbps / 1000} Mbps",
                        "Metered" to if (n.isMetered) "yes" else "no",
                    ),
                )
            }
            if (n.activeTransport == "Wi-Fi") {
                item {
                    KvCard(
                        title = "Wi-Fi",
                        accent = MaterialTheme.colorScheme.tertiary,
                        rows = listOfNotNull(
                            "SSID" to (n.wifiSsid ?: "— (needs Location permission + Location on)"),
                            n.wifiRssi?.let { "RSSI" to "$it dBm" },
                            n.wifiLinkSpeedMbps?.let { "Link" to "$it Mbps" },
                            n.wifiFrequencyMhz?.let { "Frequency" to "$it MHz" },
                        ),
                    )
                }
            }
            if (n.cellularOperator != null || n.cellularType != null) {
                item {
                    KvCard(
                        title = "Cellular",
                        accent = MaterialTheme.colorScheme.secondary,
                        rows = listOfNotNull(
                            n.cellularOperator?.let { "Operator" to it },
                            n.cellularType?.let { "Network" to it },
                        ),
                    )
                }
            }
            item { VSpace(16) }
        }
    }
}
