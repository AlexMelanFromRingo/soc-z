# Where every number comes from

A field-by-field map of SOC-Z's data sources. Useful when a value looks wrong
on a particular device — most quirks are vendor kernel quirks.

## Overview / SoC (`Soc.kt`)

| Field | Source |
|---|---|
| SoC model / manufacturer | `Build.SOC_MODEL` / `Build.SOC_MANUFACTURER` (API 31+), falling back to `ro.soc.model`, `ro.soc.manufacturer`, `ro.board.platform` system properties |
| Device model | `Build.MANUFACTURER` + `Build.MODEL` |
| Android version / SDK | `Build.VERSION.RELEASE` / `SDK_INT` |
| ABIs | `Build.SUPPORTED_ABIS` |
| CPU implementer / part / arch / features | first block of `/proc/cpuinfo` |
| Core count | `Runtime.availableProcessors()` |

## CPU (`Cpu.kt`)

Per core `N` (0–23 probed), under `/sys/devices/system/cpu/cpuN/`:

| Field | Node |
|---|---|
| Online | `online` (assumed online if the node is absent — cpu0 often has none) |
| Current clock | `cpufreq/scaling_cur_freq` (kHz → MHz) |
| Min / max clock | `cpufreq/cpuinfo_min_freq` / `cpuinfo_max_freq` |
| Governor | `cpufreq/scaling_governor` |

Temperatures: `/sys/class/thermal/thermal_zoneN/{type,temp}` for N = 0–30,
milli-°C. Zone names are entirely vendor-defined.

## GPU (`Gpu.kt` + `cpp/vk_probe.cpp`)

| Field | Source |
|---|---|
| GL vendor / renderer / version / GLSL / extensions | `glGetString` on a headless EGL14 pbuffer context |
| Vulkan extensions + capability flags | JNI: transient `VkInstance` → `vkEnumerateDeviceExtensionProperties` + instance extensions |
| Vulkan API version | `PackageManager` feature `android.hardware.vulkan.version` (decoded major.minor.patch) + `android.hardware.vulkan.level` |
| Adreno model | regex over `GL_RENDERER` |
| Live clock (Adreno) | `/sys/class/kgsl/kgsl-3d0/gpuclk` (Hz), `devfreq/min_freq`, `devfreq/max_freq`, `gpu_busy_percentage` |
| Live clock (others) | first `/sys/class/devfreq/*/` dir whose name contains `gpu`/`mali`/`kgsl`: `cur_freq`, `min_freq`, `max_freq` |

## Memory (`Memory.kt`)

`/proc/meminfo` (`MemTotal`, `MemFree`, `MemAvailable`, `Cached`, `Buffers`,
`SwapTotal`, `SwapFree`), with `ActivityManager.MemoryInfo` as fallback and for
`lowMemory`/`threshold`. ZRAM size: `/sys/block/zramN/disksize`, N = 0–7.
"Used" is defined as `MemTotal − MemAvailable`.

## Battery (`Battery.kt`)

| Field | Source |
|---|---|
| Level | `BatteryManager.BATTERY_PROPERTY_CAPACITY` |
| Current (µA) | `BATTERY_PROPERTY_CURRENT_NOW` (sign convention varies by vendor) |
| Charge counter (µAh) | `BATTERY_PROPERTY_CHARGE_COUNTER` |
| Energy counter (nWh) | `BATTERY_PROPERTY_ENERGY_COUNTER` (unsupported on most phones) |
| Cycle count | `EXTRA_CYCLE_COUNT` from the `ACTION_BATTERY_CHANGED` sticky intent, API 34+ only |
| Status / plugged / health / technology / voltage / temperature | extras of the `ACTION_BATTERY_CHANGED` sticky intent |
| Power (W) | computed: `current × voltage` |
| Design capacity (mAh) | `com.android.internal.os.PowerProfile.getBatteryCapacity()` via reflection; values ≤ 100 treated as OEM stubs |
| Health estimate | computed: `charge_counter / level × 100` vs design capacity — an approximation, noisy at low charge levels |

## Display (`Display.kt`)

`DisplayManager.displays`, per display: real metrics (`getRealMetrics`),
`refreshRate`, `supportedModes`, `hdrCapabilities`, `state`, and `rotation`
(`Surface.ROTATION_0/90/180/270` — the display's **current orientation**
relative to its natural one, shown in degrees). Polled every second.

## Storage (`Storage.kt`)

`StorageManager.storageVolumes` → `StatFs` per volume directory; falls back to
a single `StatFs(/data)` entry if the volume list is empty.

## Sensors (`Sensors.kt`)

Static descriptors: `SensorManager.getSensorList(TYPE_ALL)` — name, vendor,
string type, version, power (mA), resolution, max range, min/max delay,
wake-up and dynamic flags.

Live values: one `SensorEventListener` registered at `SENSOR_DELAY_UI` for all
non-one-shot sensors; the latest `event.values` per sensor are snapshotted to
the UI every 500 ms. Units are sensor-type specific (m/s², µT, lux, …) — values
are shown raw, up to 6 components.

## Network (`Network.kt`)

| Field | Source |
|---|---|
| Transport / bandwidth / metered | `ConnectivityManager.activeNetwork` → `NetworkCapabilities` |
| IPv4 / IPv6 | `ConnectivityManager.getLinkProperties().linkAddresses` (first global address; link-local IPv6 skipped) |
| SSID / RSSI / link speed / frequency | `WifiManager.connectionInfo` (deprecated but functional). SSID requires `ACCESS_FINE_LOCATION` **and** system Location on; `<unknown ssid>` is mapped to "not available" |
| Wi-Fi standard | `WifiInfo.getWifiStandard()` → "Wi-Fi 4/5/6/7 (802.11n/ac/ax/be)" |
| Cellular type | `TelephonyManager.dataNetworkType` (needs `READ_PHONE_STATE`) |
| Operator | `TelephonyManager.networkOperatorName` |
