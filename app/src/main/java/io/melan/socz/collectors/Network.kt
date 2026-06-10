package io.melan.socz.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.Inet6Address

data class NetworkSample(
    val activeTransport: String,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val isMetered: Boolean,
    val wifiSsid: String?,
    val wifiRssi: Int?,
    val wifiLinkSpeedMbps: Int?,
    val wifiFrequencyMhz: Int?,
    val wifiStandard: String?,
    val cellularType: String?,
    val cellularOperator: String?,
    val ipv4: String?,
    val ipv6: String?,
)

object NetworkCollector {
    fun sample(ctx: Context): NetworkSample {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }

        val transport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "other"
        }

        val (downKbps, upKbps) = caps?.let { it.linkDownstreamBandwidthKbps to it.linkUpstreamBandwidthKbps }
            ?: (0 to 0)

        val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = if (transport == "Wi-Fi") runCatching { wifi.connectionInfo }.getOrNull() else null
        // Android redacts the SSID to "<unknown ssid>" unless the app holds
        // ACCESS_FINE_LOCATION and system location is on — report that as null.
        val ssid = wifiInfo?.ssid?.trim('"')
            ?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }

        val addresses = active
            ?.let { runCatching { cm.getLinkProperties(it)?.linkAddresses }.getOrNull() }
            ?.map { it.address }
            .orEmpty()

        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val canReadPhone = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

        return NetworkSample(
            activeTransport = transport,
            downstreamKbps = downKbps,
            upstreamKbps = upKbps,
            isMetered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false,
            wifiSsid = ssid,
            wifiRssi = wifiInfo?.rssi,
            wifiLinkSpeedMbps = wifiInfo?.linkSpeed,
            wifiFrequencyMhz = wifiInfo?.frequency,
            wifiStandard = wifiInfo?.let { wifiStandardName(it.wifiStandard) },
            cellularType = if (canReadPhone) networkTypeName(tm.dataNetworkType) else null,
            cellularOperator = tm.networkOperatorName.takeIf { it.isNotBlank() },
            ipv4 = addresses.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress,
            ipv6 = addresses.filterIsInstance<Inet6Address>()
                .firstOrNull { !it.isLinkLocalAddress }?.hostAddress,
        )
    }

    internal fun wifiStandardName(s: Int): String? = when (s) {
        ScanResult.WIFI_STANDARD_LEGACY -> "legacy (802.11a/b/g)"
        ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
        ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
        ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
        ScanResult.WIFI_STANDARD_11AD -> "WiGig (802.11ad)"
        ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
        else -> null
    }

    private fun networkTypeName(t: Int) = when (t) {
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "unknown"
        else -> "type$t"
    }
}
