# SOC-Z architecture

SOC-Z is deliberately small: one activity, one navigation graph, a set of
stateless "collector" objects, and one JNI function. This document explains the
layers and the conventions, so a new screen or data source can be added without
reverse-engineering the code.

## Layers

```
┌─────────────────────────────────────────────────────┐
│ UI (Jetpack Compose, Material 3)                    │
│   MainActivity → NavHost → screens/Screens.kt       │
│   shared widgets in components/Common.kt            │
├─────────────────────────────────────────────────────┤
│ Collectors (io.melan.socz.collectors)               │
│   pure functions: system state → immutable data     │
│   no Compose imports, no caching of Context         │
├─────────────────────────────────────────────────────┤
│ Sources                                             │
│   Android APIs · /proc · /sys · JNI (libsocz.so)    │
└─────────────────────────────────────────────────────┘
```

### Collectors

Each domain lives in one file and exposes one object with a `read(…)` or
`sample(…)` function returning a data class:

| Collector | Entry point | Kind |
|---|---|---|
| `SocCollector` | `read(): SocInfo` | static, read once |
| `CpuCollector` | `sample(): CpuSample` | live, polled |
| `GpuCollector` | `read(ctx): GpuInfo` + `sampleFrequency(): GpuFreqSample?` | static info read once (creates an EGL context + Vulkan instance); clock polled from vendor sysfs |
| `MemoryCollector` | `sample(ctx): MemorySample` | live, polled |
| `BatteryCollector` | `sample(ctx): BatterySample` | live, polled |
| `DisplayCollector` | `read(ctx): List<DisplayInfo>` | live, polled (state/rotation change) |
| `StorageCollector` | `read(ctx): List<VolumeInfo>` | static, read once |
| `SensorCollector` | `read(ctx): List<SensorDesc>` + `liveValues(ctx): Flow<Map<String, FloatArray>>` | static list + push-based live values |
| `NetworkCollector` | `sample(ctx): NetworkSample` | live, polled |

Conventions:

- Collectors are **side-effect free** apart from reading; they never hold a
  `Context` or any mutable state (the one exception is documented below).
- Every filesystem read is wrapped in `runCatching` with a safe default —
  vendor kernels differ wildly in which sysfs nodes exist and are readable.
- All units are encoded in the field name (`curMhz`, `tempMilliC`,
  `capacityRemainUah`, `energyCounterNwh`). Conversion happens in the UI.
- Parsing of text formats lives in `internal` pure functions
  (`parseMeminfo`, `parseAdreno`, `decodeVulkanVersion`, `cpuinfoField`,
  `wifiStandardName`) so JVM unit tests in `app/src/test` can cover them
  without a device.

### Polling: `tickerFlow`

`Ticker.kt` defines the single primitive that drives every live screen:

```kotlin
fun <T> tickerFlow(intervalMs: Long, sample: () -> T): Flow<T> = flow {
    while (true) { emit(sample()); delay(intervalMs) }
}
```

Screens use it as:

```kotlin
val sample by remember {
    tickerFlow(500L) { CpuCollector.sample() }.flowOn(Dispatchers.IO)
}.collectAsState(initial = null)
```

`flowOn(Dispatchers.IO)` moves the blocking `/proc`–`/sys` reads off the main
thread; `collectAsState` cancels collection when the screen leaves
composition, so nothing polls in the background. Poll intervals are chosen per
domain: CPU 500 ms, memory 1–1.5 s, battery 1–2 s, display 1 s, network 2 s.

### Sensors: push, not poll

Sensor values cannot be polled — the hardware pushes them. `SensorCollector.liveValues()`
is a `callbackFlow` that:

1. registers **one** `SensorEventListener` for every non-one-shot sensor at
   `SENSOR_DELAY_UI`,
2. stores the latest values per sensor in a `ConcurrentHashMap` keyed by
   `"${type}/${name}"` (matching `SensorDesc.key`),
3. snapshots the whole map into the flow every 500 ms (so UI recomposition is
   bounded regardless of how fast a gyroscope reports),
4. unregisters everything in `awaitClose` when the screen is left.

One-shot (trigger) sensors such as significant-motion are skipped — they need
`requestTriggerSensor` and fire only once.

### The native Vulkan probe

Android has no Java/Kotlin API for Vulkan *extension* enumeration —
`PackageManager` only exposes the API level via the
`android.hardware.vulkan.version` feature. Capability flags like ray tracing
live entirely in Vulkan-land, so `cpp/vk_probe.cpp`:

1. creates a transient `VkInstance` (API 1.1 requested, loader caps it),
2. enumerates device extensions of **all** physical devices plus instance
   extensions,
3. dedups, sorts and returns them to Kotlin as a `String[]`,
4. destroys the instance.

`GpuCollector` then derives flags (`VK_KHR_ray_tracing_pipeline`,
`VK_KHR_acceleration_structure`, `VK_KHR_ray_query`, `VK_EXT_mesh_shader`,
`VK_EXT_descriptor_indexing`) from the returned set. If `libsocz.so` fails to
load or `vkCreateInstance` fails, the list is empty and the UI shows the
OpenGL data only.

The OpenGL strings are queried from a headless EGL14 context backed by a 4×4
pbuffer, torn down immediately after reading.

### Strings and localization

All user-facing labels in the UI layer come from `res/values/strings.xml` via
`stringResource(...)` — adding a translation is a matter of dropping in a
`values-xx/strings.xml`. Two deliberate exceptions stay untranslated:

- values that *are* data — `/proc/meminfo` keys, governor names, sensor type
  strings, and the status/transport names emitted by collectors;
- the shareable text report (below), which is meant to be compared across
  devices and pasted into bug trackers.

### The text report

`report/Report.kt` (`ReportBuilder.build(ctx)`) runs every collector and
formats one plain-text dump of the whole device. It is triggered by the share
action on the Overview screen, built on `Dispatchers.IO` (the GPU probe spins
up EGL + Vulkan), and handed to the system share sheet via `ACTION_SEND`.

### UI layer

- **`MainActivity`** hosts a `Scaffold` with a Material 3 `NavigationBar`
  (five primary tabs) and a `NavHost` with all nine destinations. Display,
  Storage, Sensors and Network are reached from cards on the Overview screen
  — nine bottom-bar items don't fit a phone.
- **`Screens.kt`** has one composable per destination. Every screen follows
  the same skeleton: `BaseScreen(title) { LazyColumn { … } }`.
- **`Common.kt`** holds the three shared widgets:
  - `KvCard` — rounded card with key/value rows (the default look of the app),
  - `MetricBar` — labelled `LinearProgressIndicator` for usage values,
  - `SectionTitle` — uppercase section label.
- **`Theme.kt`** — dark-first custom palette; on Android 12+ dynamic
  (Material You) colors take precedence.

### Permissions

Declared in the manifest:

- `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — install-time.
- `ACCESS_FINE_LOCATION` — runtime; the OS requires it for reading the Wi-Fi
  SSID. Requested by `NetworkScreen` on first composition.
- `READ_PHONE_STATE` — runtime; required for
  `TelephonyManager.dataNetworkType`. Requested together with location.

Collectors must keep working when permissions are denied: they check with
`ContextCompat.checkSelfPermission` and return `null` fields, and the screens
render placeholder rows. The 2-second network ticker means freshly granted
permissions take effect without any manual refresh.

## Adding a new screen — checklist

1. Create `collectors/Foo.kt`: a `FooInfo` data class + `FooCollector.read()/sample()`.
   Wrap every external read in `runCatching`. Put units in field names.
2. Add `FooScreen()` to `ui/screens/Screens.kt` using `BaseScreen` +
   `KvCard`/`MetricBar`. Static data → `produceState` + `withContext(Dispatchers.IO)`;
   live data → `tickerFlow(…).flowOn(Dispatchers.IO)`.
3. Register the route in `MainActivity`'s `NavHost`; add a `Tab` if it deserves
   the bottom bar, otherwise a `SecondaryNavRow` on the Overview screen.
4. If a permission is needed, declare it in the manifest, request it at
   runtime in the screen, and make the collector degrade gracefully without it.

## Build pipeline

- AGP 8.7.3, Kotlin 2.0.21 with the Compose compiler plugin, JDK 17.
- `compileSdk`/`targetSdk` 35, `minSdk` 31, single ABI `arm64-v8a`.
- Native code: CMake 3.22.1 + NDK 26.3.11579264, C++17, links against the NDK
  `vulkan` and `log` libraries.
- Release: R8 minify + resource shrinking; signing config is read from
  `local.properties` and skipped if absent (see README).
- CI: `.github/workflows/build.yml` runs `assembleDebug lintDebug
  testDebugUnitTest` on every push/PR and uploads the debug APK artifact.
