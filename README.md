# SOC-Z

[![build](https://github.com/AlexMelanFromRingo/soc-z/actions/workflows/build.yml/badge.svg)](https://github.com/AlexMelanFromRingo/soc-z/actions/workflows/build.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A lightweight hardware inspector for Android — a CPU-Z-style app that shows what
is actually inside your phone: SoC, per-core CPU clocks, GPU capabilities
(including a real Vulkan extension probe via JNI), memory, battery, storage,
display, sensors with live readings, and network state.

Built with Kotlin, Jetpack Compose (Material 3) and a small C++ native library.
No ads, no analytics, no network access — everything is read locally from
Android APIs, `/proc` and `/sys`.

## Features

| Tab / screen | What it shows |
|---|---|
| **Overview** | Device model, SoC vendor/model, Android version, ABIs, plus live CPU average clock, CPU temperatures, RAM/swap usage and battery summary |
| **CPU** | Per-core live frequency (0.5 s refresh), min/max clocks, online/offline state, scaling governor, all thermal zones |
| **GPU** | OpenGL ES vendor/renderer/version/GLSL, full OpenGL and Vulkan extension lists, Vulkan API version, capability flags (ray tracing pipeline, acceleration structure, ray query, mesh shaders, bindless textures) and a live GPU clock/busy gauge where the vendor kernel exposes it |
| **Memory** | Live RAM / cached / swap (ZRAM) usage bars and the raw `/proc/meminfo` numbers, low-memory state and threshold |
| **Battery** | Level, charge/discharge current, voltage, computed power draw, temperature, charge counter, design capacity with an estimated battery-health percentage, cycle count (Android 14+), health, technology |
| **Display** | Per-display resolution, density, current and supported refresh rates, HDR types, power state, current rotation — updates live |
| **Storage** | Every mounted volume with used/total bars, primary/removable/emulated flags |
| **Sensors** | Every hardware sensor with vendor, type, range, resolution, power draw — and **live values** updating twice a second |
| **Network** | Active transport, link bandwidth, metered flag, IPv4/IPv6 addresses; Wi-Fi SSID/standard (Wi-Fi 4/5/6/7)/RSSI/link speed/frequency; cellular operator and network type (LTE / 5G NR / …) |

The five primary tabs (Overview, CPU, GPU, Memory, Battery) live in the bottom
navigation bar; Display, Storage, Sensors and Network open from cards on the
Overview screen. The share button on the Overview screen exports the whole
device profile as a plain-text report (for bug trackers, forums, comparisons).

## Screenshots

<!-- TODO: drop PNGs into docs/screenshots/ and uncomment.
     Capture with: adb exec-out screencap -p > docs/screenshots/overview.png
<p align="center">
  <img src="docs/screenshots/overview.png" width="24%" />
  <img src="docs/screenshots/cpu.png" width="24%" />
  <img src="docs/screenshots/gpu.png" width="24%" />
  <img src="docs/screenshots/sensors.png" width="24%" />
</p>
-->
*Coming soon.*

## Requirements

- Android 12 (API 31) or newer
- 64-bit ARM device (`arm64-v8a` — the only packaged ABI)
- Vulkan capability detection requires a Vulkan-capable GPU driver (virtually
  all API 31+ devices); the app degrades gracefully if the loader is missing

## Permissions

All permissions are optional — screens simply show fewer rows if they are denied.

| Permission | Why |
|---|---|
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Active transport, bandwidth, Wi-Fi link info (install-time, no prompt) |
| `ACCESS_FINE_LOCATION` | **Required by Android to read the Wi-Fi SSID.** Requested at runtime when you open the Network screen |
| `READ_PHONE_STATE` | Cellular network type (LTE / 5G NR / …). Requested at runtime on the Network screen |

> **Why does an info app ask for Location?** Since Android 8.1 the OS treats
> the SSID/BSSID of the connected network as location data (nearby networks
> reveal where you are). Without fine location — and with system Location
> services switched **on** — every app gets `<unknown ssid>`. SOC-Z uses it
> for nothing else and never touches the internet.

## Building

Prerequisites:

- JDK 17
- Android SDK with platform 35
- Android NDK **26.3.11579264** and CMake **3.22.1** (installable via SDK Manager)

```bash
git clone https://github.com/AlexMelanFromRingo/soc-z.git
cd soc-z
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build + install on a connected device
./gradlew testDebugUnitTest      # parser unit tests
```

CI (GitHub Actions) builds, lints and tests every push and attaches the debug
APK as an artifact.

### Release builds

Release builds are minified (R8) and require signing credentials in
`local.properties` (never committed):

```properties
release.store.file=/path/to/keystore.jks
release.store.password=...
release.key.alias=...
release.key.password=...
```

Then:

```bash
./gradlew assembleRelease        # → app/build/outputs/apk/release/app-release.apk
```

Without these properties the release build is produced unsigned.

## Architecture

```
app/src/main/
├── java/io/melan/socz/
│   ├── MainActivity.kt          # single activity, bottom nav + NavHost
│   ├── collectors/              # pure data readers, one file per domain
│   │   ├── Soc.kt Cpu.kt Gpu.kt Memory.kt Battery.kt
│   │   ├── Display.kt Storage.kt Sensors.kt Network.kt
│   │   └── Ticker.kt            # tickerFlow(): poll-every-N-ms Flow helper
│   └── ui/
│       ├── screens/Screens.kt   # one composable per tab
│       ├── components/Common.kt # KvCard, MetricBar, SectionTitle
│       └── theme/Theme.kt       # M3 dark/light + dynamic color
└── cpp/
    ├── vk_probe.cpp             # JNI Vulkan extension probe
    └── CMakeLists.txt
```

The pattern throughout: a **collector** object turns system state into an
immutable data class; a screen either reads it once (`produceState`) for
static data or polls it on a `tickerFlow` (off the main thread via
`Dispatchers.IO`) for live data. The Vulkan extension list is the one thing
Android exposes no Java API for, so a tiny native library creates a transient
`VkInstance` and enumerates physical-device extensions.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full write-up and
[docs/DATA-SOURCES.md](docs/DATA-SOURCES.md) for exactly where each number
comes from.

## Known limitations

- **CPU utilization** per core cannot be measured — Android's SELinux policy
  hides `/proc/stat` from apps since 8.0. The bars show the position of the
  current clock within the min–max range instead.
- **GPU clock** comes from vendor sysfs (kgsl for Adreno, devfreq otherwise);
  on devices where SELinux hides those nodes the card is simply absent.
- **Thermal zones and ZRAM** paths are vendor-specific; some devices expose
  none of them without root.
- **Battery cycle count** requires Android 14+ and OEM support — shown as `—`
  otherwise. **Design capacity** comes from the OEM power profile via
  reflection and may be missing or stubbed on some devices; the health
  estimate divides the charge counter by the current level, so it is noisy at
  low charge.
- **Current sign convention** (charging positive vs negative) varies between
  vendors; the raw value is shown as reported.
- Only `arm64-v8a` is packaged. x86_64 emulators need an added ABI filter.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| SSID shows `—` | Grant the Location permission when prompted on the Network screen *and* make sure system Location is on |
| Cellular type missing | Deny/grant state of `READ_PHONE_STATE`; re-open the Network screen |
| Vulkan section says *not supported* | Device/driver has no Vulkan loader — OpenGL info is still shown |
| Some cores show `offline` at 0 MHz | Normal — the kernel hot-unplugs idle cores; they come back under load |

## License

[MIT](LICENSE) © 2026 Alex Melan
