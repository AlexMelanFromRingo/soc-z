package io.melan.socz.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.melan.socz.R
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
import io.melan.socz.report.ReportBuilder
import io.melan.socz.ui.components.KvCard
import io.melan.socz.ui.components.MetricBar
import io.melan.socz.ui.components.SectionTitle
import io.melan.socz.ui.components.VSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaseScreen(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(title) },
            actions = actions,
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
    val scope = rememberCoroutineScope()
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

    BaseScreen(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = {
                scope.launch {
                    val report = withContext(Dispatchers.IO) { ReportBuilder.build(ctx) }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SOC-Z hardware report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }
            }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4); SectionTitle(stringResource(R.string.section_device)) }
            item {
                val s = soc ?: return@item
                KvCard(rows = listOf(
                    stringResource(R.string.row_model) to s.deviceModel,
                    stringResource(R.string.row_soc) to "${s.socManufacturer} ${s.socModel}",
                    stringResource(R.string.row_android) to "${s.androidVersion} (SDK ${s.androidSdk})",
                    stringResource(R.string.row_cores) to s.coreCount.toString(),
                    stringResource(R.string.row_abis) to s.abis.joinToString(", "),
                    stringResource(R.string.row_hardware) to s.buildHardware,
                    stringResource(R.string.row_platform) to s.buildPlatform,
                ))
            }
            item { SectionTitle(stringResource(R.string.section_live)) }
            item {
                val cpu = cpuSample
                if (cpu != null) {
                    val avgMhz = cpu.cores.filter { it.online }
                        .map { it.curMhz }.average().toLong()
                    val maxMhz = cpu.cores.maxOfOrNull { it.maxMhz } ?: 0L
                    KvCard(
                        rows = listOf(
                            stringResource(R.string.row_cpu_avg) to
                                stringResource(R.string.value_cpu_avg, avgMhz, maxMhz),
                            stringResource(R.string.row_cpu_temps) to cpu.tempMilliC.entries.joinToString(", ") {
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
                            Text(stringResource(R.string.tab_memory),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            MetricBar(
                                label = stringResource(R.string.label_ram),
                                valueText = "${mem.usedKb / 1024} / ${mem.totalKb / 1024} MiB",
                                fraction = mem.usedKb.toFloat() / mem.totalKb.coerceAtLeast(1L),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                            if (mem.swapTotalKb > 0) {
                                MetricBar(
                                    label = stringResource(R.string.label_swap_zram),
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
                    title = stringResource(R.string.tab_battery),
                    accent = MaterialTheme.colorScheme.secondary,
                    rows = listOf(
                        stringResource(R.string.row_level) to "${b.levelPercent}%",
                        stringResource(R.string.row_status) to "${b.status} (${b.plugged})",
                        stringResource(R.string.row_temperature) to "${"%.1f".format(b.temperatureC)} °C",
                        stringResource(R.string.row_current) to "${b.currentUa / 1000} mA",
                        stringResource(R.string.row_voltage) to "${b.voltageMv} mV",
                    ),
                )
            }
            item { SectionTitle(stringResource(R.string.section_other)) }
            item { SecondaryNavRow(Icons.Outlined.SmartDisplay, stringResource(R.string.title_display)) { onOpenSecondary("display") } }
            item { SecondaryNavRow(Icons.Outlined.Storage, stringResource(R.string.title_storage)) { onOpenSecondary("storage") } }
            item { SecondaryNavRow(Icons.Outlined.Sensors, stringResource(R.string.title_sensors)) { onOpenSecondary("sensors") } }
            item { SecondaryNavRow(Icons.Outlined.NetworkCell, stringResource(R.string.title_network)) { onOpenSecondary("network") } }
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
    BaseScreen(stringResource(R.string.tab_cpu)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { VSpace(4); SectionTitle(stringResource(R.string.section_cores)) }
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
                                Text(stringResource(R.string.core_label, core.index),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(80.dp))
                                Text(stringResource(if (core.online) R.string.core_online else R.string.core_offline),
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
                item { SectionTitle(stringResource(R.string.section_thermal_zones)) }
                item {
                    KvCard(rows = cpu.tempMilliC.entries.map { (k, v) ->
                        k to "${"%.1f".format(v / 1000f)} °C"
                    }.ifEmpty { listOf(stringResource(R.string.value_none) to "—") })
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
    val freq by remember {
        tickerFlow(1000L) { GpuCollector.sampleFrequency() }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen(stringResource(R.string.tab_gpu)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            item {
                val f = freq ?: return@item
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.card_gpu_clock),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        MetricBar(
                            label = "${f.minMhz} – ${f.maxMhz} MHz",
                            valueText = "${f.curMhz} MHz",
                            fraction = (f.curMhz - f.minMhz).toFloat() /
                                (f.maxMhz - f.minMhz).coerceAtLeast(1L).toFloat(),
                        )
                        f.busyPercent?.let { busy ->
                            MetricBar(
                                label = stringResource(R.string.label_busy),
                                valueText = "$busy%",
                                fraction = busy / 100f,
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
            val g = gpu ?: return@LazyColumn
            item {
                KvCard(
                    title = stringResource(R.string.card_opengl),
                    accent = MaterialTheme.colorScheme.primary,
                    rows = listOf(
                        stringResource(R.string.row_vendor) to g.openGlVendor,
                        stringResource(R.string.row_renderer) to g.openGlRenderer,
                        stringResource(R.string.row_version) to g.openGlVersion,
                        stringResource(R.string.row_glsl) to g.glslVersion,
                        stringResource(R.string.row_adreno) to (g.adrenoModel?.toString() ?: "—"),
                    ),
                )
            }
            item {
                KvCard(
                    title = stringResource(R.string.card_vulkan),
                    accent = MaterialTheme.colorScheme.tertiary,
                    rows = listOf(
                        stringResource(R.string.row_api_version) to
                            (g.vulkanApiVersion ?: stringResource(R.string.not_supported)),
                        stringResource(R.string.row_rt_pipeline) to flag(g.supportsRayTracingPipeline),
                        stringResource(R.string.row_accel_structure) to flag(g.supportsAccelerationStructure),
                        stringResource(R.string.row_ray_query) to flag(g.supportsRayQuery),
                        stringResource(R.string.row_mesh_shader) to flag(g.supportsMeshShader),
                        stringResource(R.string.row_bindless) to flag(g.supportsBindlessTextures),
                    ),
                )
            }
            item { SectionTitle(stringResource(R.string.section_vulkan_extensions, g.vulkanExtensions.size)) }
            items(g.vulkanExtensions) { e ->
                Text(e, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp))
            }
            item { SectionTitle(stringResource(R.string.section_opengl_extensions, g.openGlExtensions.size)) }
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

@Composable
private fun flag(b: Boolean) = stringResource(if (b) R.string.flag_yes else R.string.flag_no)

/* ============================ MEMORY ============================ */

@Composable
fun MemoryScreen() {
    val ctx = LocalContext.current
    val sample by remember(ctx) {
        tickerFlow(1000L) { MemoryCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen(stringResource(R.string.tab_memory)) {
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
                        MetricBar(stringResource(R.string.label_ram_used),
                            "${m.usedKb / 1024} / ${m.totalKb / 1024} MiB",
                            m.usedKb.toFloat() / m.totalKb.coerceAtLeast(1L),
                            MaterialTheme.colorScheme.primary)
                        MetricBar(stringResource(R.string.label_cached),
                            "${m.cachedKb / 1024} MiB",
                            m.cachedKb.toFloat() / m.totalKb.coerceAtLeast(1L),
                            MaterialTheme.colorScheme.tertiary)
                        if (m.swapTotalKb > 0) {
                            MetricBar(stringResource(R.string.label_swap_zram),
                                "${(m.swapTotalKb - m.swapFreeKb) / 1024} / ${m.swapTotalKb / 1024} MiB",
                                (m.swapTotalKb - m.swapFreeKb).toFloat() /
                                    m.swapTotalKb.coerceAtLeast(1L),
                                MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            item {
                // /proc/meminfo keys are kernel identifiers — left untranslated on purpose.
                KvCard(rows = listOf(
                    "MemTotal" to "${m.totalKb / 1024} MiB",
                    "MemFree" to "${m.freeKb / 1024} MiB",
                    "MemAvailable" to "${m.availKb / 1024} MiB",
                    "Cached" to "${m.cachedKb / 1024} MiB",
                    "Buffers" to "${m.buffersKb / 1024} MiB",
                    "SwapTotal" to "${m.swapTotalKb / 1024} MiB",
                    "SwapFree" to "${m.swapFreeKb / 1024} MiB",
                    "ZRAM" to "${m.zramKb / 1024} MiB",
                    stringResource(R.string.row_low_memory) to
                        stringResource(if (m.lowMemory) R.string.value_alert else R.string.value_no),
                    stringResource(R.string.row_threshold) to "${m.threshold / 1024} MiB",
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
    BaseScreen(stringResource(R.string.tab_battery)) {
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
                            label = stringResource(R.string.label_level),
                            valueText = "${b.levelPercent}%",
                            fraction = b.levelPercent / 100f,
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            item {
                val rows = buildList {
                    add(stringResource(R.string.row_temperature) to "${"%.1f".format(b.temperatureC)} °C")
                    add(stringResource(R.string.row_current) to "${b.currentUa / 1000} mA")
                    add(stringResource(R.string.row_voltage) to "${b.voltageMv} mV")
                    add(stringResource(R.string.row_power) to
                        "${"%.2f".format(b.currentUa / 1_000_000.0 * b.voltageMv / 1000.0)} W")
                    add(stringResource(R.string.row_charge_counter) to "${b.capacityRemainUah / 1000} mAh")
                    b.designCapacityMah?.let { design ->
                        add(stringResource(R.string.row_design_capacity) to "${design.toInt()} mAh")
                        if (b.levelPercent in 1..100 && b.capacityRemainUah > 0) {
                            val estFullMah = b.capacityRemainUah / 1000.0 * 100.0 / b.levelPercent
                            add(stringResource(R.string.row_full_charge_est) to "${estFullMah.toInt()} mAh")
                            add(stringResource(R.string.row_health_est) to
                                "${(estFullMah / design * 100).toInt()} %")
                        }
                    }
                    add(stringResource(R.string.row_cycles) to
                        if (b.chargingCycles >= 0) b.chargingCycles.toString() else "—")
                    add(stringResource(R.string.row_plugged) to b.plugged)
                    add(stringResource(R.string.row_technology) to b.technology)
                    add(stringResource(R.string.row_health) to b.health)
                }
                KvCard(rows = rows)
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
    BaseScreen(stringResource(R.string.title_display)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            items(displays) { d ->
                KvCard(
                    title = d.name,
                    rows = listOf(
                        stringResource(R.string.row_resolution) to "${d.widthPx} × ${d.heightPx} px",
                        stringResource(R.string.row_density) to "${d.densityDpi} dpi",
                        stringResource(R.string.row_refresh) to "${"%.1f".format(d.refreshHz)} Hz",
                        stringResource(R.string.row_supported) to
                            d.supportedRefreshRates.joinToString(", ") { "${"%.0f".format(it)} Hz" },
                        stringResource(R.string.row_hdr) to d.hdrTypes.joinToString(", ").ifBlank { "—" },
                        stringResource(R.string.row_state) to d.state,
                        stringResource(R.string.row_rotation) to "${d.rotationDeg}°",
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
    BaseScreen(stringResource(R.string.title_storage)) {
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
                            label = stringResource(R.string.label_used),
                            valueText = "${v.usedBytes / (1024L * 1024L * 1024L)} / " +
                                "${v.totalBytes / (1024L * 1024L * 1024L)} GiB",
                            fraction = v.usedBytes.toFloat() / v.totalBytes.coerceAtLeast(1L),
                        )
                        Text(
                            text = listOfNotNull(
                                if (v.isPrimary) stringResource(R.string.value_primary) else null,
                                if (v.isRemovable) stringResource(R.string.value_removable) else null,
                                if (v.isEmulated) stringResource(R.string.value_emulated) else null,
                            ).joinToString("  "),
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
    BaseScreen(stringResource(R.string.title_sensors)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { VSpace(4); SectionTitle(stringResource(R.string.section_found, sensors.size)) }
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
                            stringResource(R.string.sensor_meta, s.vendor,
                                s.maxRange.toString(), s.resolution.toString(), s.powerMa.toString()) +
                                if (s.isWakeUp) stringResource(R.string.sensor_wakeup_suffix) else "",
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
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        ).filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    val sample by remember(ctx) {
        tickerFlow(2000L) { NetworkCollector.sample(ctx) }.flowOn(Dispatchers.IO)
    }.collectAsState(initial = null)
    BaseScreen(stringResource(R.string.title_network)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VSpace(4) }
            val n = sample ?: return@LazyColumn
            item {
                KvCard(
                    title = stringResource(R.string.card_active_connection),
                    accent = MaterialTheme.colorScheme.primary,
                    rows = listOfNotNull(
                        stringResource(R.string.row_transport) to n.activeTransport,
                        stringResource(R.string.row_downstream) to "${n.downstreamKbps / 1000} Mbps",
                        stringResource(R.string.row_upstream) to "${n.upstreamKbps / 1000} Mbps",
                        stringResource(R.string.row_metered) to
                            stringResource(if (n.isMetered) R.string.value_yes else R.string.value_no),
                        n.ipv4?.let { stringResource(R.string.row_ipv4) to it },
                        n.ipv6?.let { stringResource(R.string.row_ipv6) to it },
                    ),
                )
            }
            if (n.activeTransport == "Wi-Fi") {
                item {
                    KvCard(
                        title = stringResource(R.string.card_wifi),
                        accent = MaterialTheme.colorScheme.tertiary,
                        rows = listOfNotNull(
                            stringResource(R.string.row_ssid) to
                                (n.wifiSsid ?: stringResource(R.string.ssid_needs_location)),
                            n.wifiStandard?.let { stringResource(R.string.row_standard) to it },
                            n.wifiRssi?.let { stringResource(R.string.row_rssi) to "$it dBm" },
                            n.wifiLinkSpeedMbps?.let { stringResource(R.string.row_link) to "$it Mbps" },
                            n.wifiFrequencyMhz?.let { stringResource(R.string.row_frequency) to "$it MHz" },
                        ),
                    )
                }
            }
            if (n.cellularOperator != null || n.cellularType != null) {
                item {
                    KvCard(
                        title = stringResource(R.string.card_cellular),
                        accent = MaterialTheme.colorScheme.secondary,
                        rows = listOfNotNull(
                            n.cellularOperator?.let { stringResource(R.string.row_operator) to it },
                            n.cellularType?.let { stringResource(R.string.row_network) to it },
                        ),
                    )
                }
            }
            item { VSpace(16) }
        }
    }
}
