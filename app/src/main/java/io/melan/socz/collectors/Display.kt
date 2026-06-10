package io.melan.socz.collectors

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

data class DisplayInfo(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshHz: Float,
    val supportedRefreshRates: List<Float>,
    val hdrTypes: List<String>,
    val state: String,
    val rotationDeg: Int,
)

object DisplayCollector {
    fun read(ctx: Context): List<DisplayInfo> {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.map { d ->
            val metrics = android.util.DisplayMetrics().apply { d.getRealMetrics(this) }
            DisplayInfo(
                name = d.name,
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                refreshHz = d.refreshRate,
                supportedRefreshRates = d.supportedModes.map { it.refreshRate }.distinct(),
                hdrTypes = d.hdrCapabilities?.supportedHdrTypes?.map { hdrTypeName(it) } ?: emptyList(),
                state = stateName(d.state),
                rotationDeg = d.rotation * 90,
            )
        }
    }

    private fun stateName(s: Int) = when (s) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_UNKNOWN -> "UNKNOWN"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "?"
    }

    private fun hdrTypeName(t: Int) = when (t) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "DolbyVision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "type$t"
    }
}
